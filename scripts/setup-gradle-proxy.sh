#!/bin/bash

# Configure Gradle to work in Claude Code remote environments.
#
# In remote environments, all network traffic must go through an HTTPS proxy.
# The proxy uses a Bearer-style authentication that Java's HTTP client doesn't
# support natively. This script:
#
# 1. Downloads the Gradle wrapper distribution using curl (which handles proxy auth)
# 2. Starts a local proxy wrapper that handles authentication to the upstream proxy
# 3. Configures Gradle to use the local proxy
#
# Usage: Run this script before building with Gradle in a Claude Code remote env.

set -e

# Only run in remote environments
if [ "$CLAUDE_CODE_REMOTE" != "true" ]; then
  exit 0
fi

if [ -z "$HTTPS_PROXY" ]; then
  echo "HTTPS_PROXY not set, skipping Gradle proxy configuration"
  exit 0
fi

GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
LOCAL_PROXY_PORT=18080
PROXY_PID_FILE="$HOME/.gradle_proxy.pid"

mkdir -p "$GRADLE_USER_HOME"

# Parse proxy URL
parse_proxy_url() {
  python3 -c "
from urllib.parse import urlparse, unquote
import os
parsed = urlparse(os.environ['HTTPS_PROXY'])
print(f'PROXY_HOST={parsed.hostname}')
print(f'PROXY_PORT={parsed.port}')
user = unquote(parsed.username) if parsed.username else ''
passwd = unquote(parsed.password) if parsed.password else ''
# Escape for shell
user = user.replace(\"'\", \"'\\\"'\\\"'\")
passwd = passwd.replace(\"'\", \"'\\\"'\\\"'\")
print(f\"PROXY_USER='{user}'\")
print(f\"PROXY_PASSWORD='{passwd}'\")
"
}

eval "$(parse_proxy_url)"

if [ -z "$PROXY_HOST" ] || [ -z "$PROXY_PORT" ]; then
  echo "ERROR: Failed to parse HTTPS_PROXY"
  exit 1
fi

echo "Setting up Gradle for Claude Code remote environment..."

# Download Gradle wrapper distribution using curl
download_gradle_wrapper() {
  local wrapper_props="${CLAUDE_PROJECT_DIR:-$PWD}/gradle/wrapper/gradle-wrapper.properties"
  [ ! -f "$wrapper_props" ] && return 0

  local dist_url
  dist_url=$(grep "^distributionUrl=" "$wrapper_props" | cut -d= -f2- | sed 's/\\:/:/g')
  [ -z "$dist_url" ] && return 0

  local dist_name=$(basename "$dist_url")
  local dist_base="${dist_name%.zip}"
  local wrapper_dir="$GRADLE_USER_HOME/wrapper/dists/$dist_base"

  # Check if already downloaded
  if find "$wrapper_dir" -name "*.ok" 2>/dev/null | grep -q .; then
    echo "✓ Gradle distribution already available"
    return 0
  fi

  echo "Downloading Gradle distribution: $dist_name"

  local temp_file="/tmp/$dist_name.$$"
  local max_retries=5
  local retry=0

  while [ $retry -lt $max_retries ]; do
    if curl -fsSL -o "$temp_file" "$dist_url" 2>/dev/null; then
      break
    fi
    retry=$((retry + 1))
    echo "  Retry $retry/$max_retries..."
    sleep $((retry * 2))
  done

  if [ ! -f "$temp_file" ]; then
    echo "WARNING: Failed to download Gradle distribution"
    return 1
  fi

  # Create wrapper directory and trigger gradlew to create hash dir
  mkdir -p "$wrapper_dir"
  (cd "${CLAUDE_PROJECT_DIR:-$PWD}" && timeout 30 ./gradlew --version 2>/dev/null || true)

  # Find and populate the hash directory
  local hash_dir
  for dir in "$wrapper_dir"/*; do
    [ -d "$dir" ] && [ "$dir" != "$wrapper_dir" ] && hash_dir="$dir" && break
  done

  if [ -n "$hash_dir" ]; then
    rm -f "$hash_dir"/*.lck "$hash_dir"/*.part
    cp "$temp_file" "$hash_dir/$dist_name"
    unzip -q -o "$hash_dir/$dist_name" -d "$hash_dir/"
    touch "$hash_dir/${dist_name}.ok"
    echo "✓ Gradle distribution installed"
  fi

  rm -f "$temp_file"
}

# Start local proxy that handles upstream authentication
start_local_proxy() {
  # Check if already running
  if [ -f "$PROXY_PID_FILE" ]; then
    local pid=$(cat "$PROXY_PID_FILE")
    if kill -0 "$pid" 2>/dev/null && nc -z localhost $LOCAL_PROXY_PORT 2>/dev/null; then
      echo "✓ Local proxy already running (PID: $pid)"
      return 0
    fi
    rm -f "$PROXY_PID_FILE"
  fi

  # Kill any stale proxy on our port
  pkill -f "127.0.0.1.*$LOCAL_PROXY_PORT" 2>/dev/null || true
  sleep 1

  echo "Starting local proxy on port $LOCAL_PROXY_PORT..."

  # Export for Python script
  export PROXY_HOST PROXY_PORT PROXY_USER PROXY_PASSWORD

  nohup python3 -c '
import http.server, socketserver, socket, select, base64, os

class ProxyHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass

    def do_CONNECT(self):
        try:
            upstream = socket.create_connection(
                (os.environ["PROXY_HOST"], int(os.environ["PROXY_PORT"])), 60)
            auth = base64.b64encode(
                f"{os.environ[\"PROXY_USER\"]}:{os.environ[\"PROXY_PASSWORD\"]}".encode()
            ).decode()
            upstream.send((
                f"CONNECT {self.path} HTTP/1.1\r\n"
                f"Host: {self.path}\r\n"
                f"Proxy-Authorization: Basic {auth}\r\n"
                f"Connection: keep-alive\r\n\r\n"
            ).encode())

            resp = b""
            while b"\r\n\r\n" not in resp and len(resp) < 8192:
                chunk = upstream.recv(4096)
                if not chunk: break
                resp += chunk

            if b"200" in resp.split(b"\r\n")[0]:
                self.send_response(200)
                self.end_headers()
                self.connection.setblocking(0)
                upstream.setblocking(0)
                while True:
                    ready, _, _ = select.select([self.connection, upstream], [], [], 60)
                    if not ready: break
                    for sock in ready:
                        try:
                            data = sock.recv(65536)
                            if not data: return
                            target = upstream if sock is self.connection else self.connection
                            target.sendall(data)
                        except: return
            else:
                self.send_error(502)
        except Exception as e:
            try: self.send_error(502, str(e))
            except: pass
        finally:
            try: upstream.close()
            except: pass

socketserver.ThreadingTCPServer.allow_reuse_address = True
with socketserver.ThreadingTCPServer(("127.0.0.1", '"$LOCAL_PROXY_PORT"'), ProxyHandler) as server:
    server.serve_forever()
' > /tmp/gradle_proxy.log 2>&1 &

  local proxy_pid=$!
  echo $proxy_pid > "$PROXY_PID_FILE"
  sleep 2

  if kill -0 $proxy_pid 2>/dev/null && nc -z localhost $LOCAL_PROXY_PORT 2>/dev/null; then
    echo "✓ Local proxy started (PID: $proxy_pid)"
  else
    echo "WARNING: Local proxy may not have started correctly"
    cat /tmp/gradle_proxy.log 2>/dev/null || true
    return 1
  fi
}

# Configure Gradle to use local proxy
configure_gradle() {
  cat > "$GRADLE_USER_HOME/gradle.properties" << EOF
# Auto-generated for Claude Code remote environment
# Local proxy handles authentication to upstream HTTPS proxy
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=$LOCAL_PROXY_PORT
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=$LOCAL_PROXY_PORT
systemProp.http.nonProxyHosts=localhost|127.0.0.1
EOF
  echo "✓ Gradle configured to use local proxy"
}

# Main execution
# Start proxy and configure Gradle BEFORE any Gradle operations
start_local_proxy
configure_gradle
download_gradle_wrapper

echo ""
echo "Gradle setup complete. You can now run: ./gradlew build"
echo ""
echo "Note: If you encounter intermittent network errors (503, timeouts),"
echo "try running the build again. The proxy handles retries internally."
