#!/bin/bash

# Configure Gradle to work in Claude Code remote environments.
#
# In remote environments, all network traffic must go through an HTTPS proxy.
# The proxy uses a Bearer-style authentication that Java's HTTP client doesn't
# support natively. This script:
#
# 1. Starts a local proxy wrapper that handles authentication to the upstream proxy
# 2. Configures Gradle and JAVA_TOOL_OPTIONS to use the local proxy
# 3. Downloads the Gradle wrapper distribution using curl (which handles proxy auth)
#
# The local proxy handles both CONNECT (HTTPS tunneling) and HTTP methods,
# with automatic retry logic for transient errors (503, connection failures).
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
  pkill -f "gradle_auth_proxy" 2>/dev/null || true
  sleep 1

  echo "Starting local proxy on port $LOCAL_PROXY_PORT..."

  # Export for Python script
  export PROXY_HOST PROXY_PORT PROXY_USER PROXY_PASSWORD

  # Create a standalone proxy script for better process management
  cat > /tmp/gradle_auth_proxy.py << 'PROXY_SCRIPT'
#!/usr/bin/env python3
"""
Local proxy that adds authentication to upstream proxy requests.
Handles both CONNECT (HTTPS tunneling) and HTTP methods with retry logic.
"""
import http.server
import socketserver
import socket
import select
import base64
import os
import sys
import time
import urllib.request
import urllib.error

PROXY_HOST = os.environ["PROXY_HOST"]
PROXY_PORT = int(os.environ["PROXY_PORT"])
PROXY_USER = os.environ["PROXY_USER"]
PROXY_PASSWORD = os.environ["PROXY_PASSWORD"]
MAX_RETRIES = 3
RETRY_DELAY = 1.0

def get_auth_header():
    auth = base64.b64encode(f"{PROXY_USER}:{PROXY_PASSWORD}".encode()).decode()
    return f"Basic {auth}"

def connect_upstream():
    """Create connection to upstream proxy with retries."""
    for attempt in range(MAX_RETRIES):
        try:
            sock = socket.create_connection((PROXY_HOST, PROXY_PORT), timeout=30)
            sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            return sock
        except (socket.error, OSError) as e:
            if attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_DELAY * (attempt + 1))
            else:
                raise

class ProxyHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):
        pass  # Suppress logging for cleaner output

    def log_error(self, format, *args):
        sys.stderr.write(f"[PROXY ERROR] {format % args}\n")
        sys.stderr.flush()

    def do_CONNECT(self):
        """Handle HTTPS tunneling via CONNECT method."""
        for attempt in range(MAX_RETRIES):
            upstream = None
            try:
                upstream = connect_upstream()

                # Send CONNECT request to upstream proxy with auth
                connect_req = (
                    f"CONNECT {self.path} HTTP/1.1\r\n"
                    f"Host: {self.path}\r\n"
                    f"Proxy-Authorization: {get_auth_header()}\r\n"
                    f"Proxy-Connection: keep-alive\r\n"
                    f"\r\n"
                )
                upstream.sendall(connect_req.encode())

                # Read response from upstream
                upstream.settimeout(30)
                resp = b""
                while b"\r\n\r\n" not in resp and len(resp) < 8192:
                    chunk = upstream.recv(4096)
                    if not chunk:
                        break
                    resp += chunk

                if not resp:
                    raise ConnectionError("Empty response from upstream proxy")

                status_line = resp.split(b"\r\n")[0].decode("utf-8", errors="replace")

                # Check for success (200) or retryable errors (503, 502)
                if b" 200 " in resp.split(b"\r\n")[0]:
                    # Success - establish tunnel
                    self.send_response(200, "Connection Established")
                    self.end_headers()
                    self._tunnel_data(upstream)
                    return
                elif b" 503 " in resp.split(b"\r\n")[0] or b" 502 " in resp.split(b"\r\n")[0]:
                    # Retryable error
                    upstream.close()
                    if attempt < MAX_RETRIES - 1:
                        time.sleep(RETRY_DELAY * (attempt + 1))
                        continue
                    else:
                        self.send_error(503, f"Upstream proxy error after {MAX_RETRIES} retries: {status_line}")
                        return
                else:
                    # Non-retryable error
                    self.send_error(502, f"Upstream proxy error: {status_line}")
                    return

            except Exception as e:
                if upstream:
                    try:
                        upstream.close()
                    except:
                        pass
                if attempt < MAX_RETRIES - 1:
                    time.sleep(RETRY_DELAY * (attempt + 1))
                    continue
                else:
                    try:
                        self.send_error(502, f"Connection failed: {e}")
                    except:
                        pass
                    return

    def _tunnel_data(self, upstream):
        """Bidirectional data transfer for CONNECT tunneling."""
        try:
            self.connection.setblocking(False)
            upstream.setblocking(False)

            while True:
                readable, _, exceptional = select.select(
                    [self.connection, upstream], [], [self.connection, upstream], 120
                )

                if exceptional:
                    break

                if not readable:
                    # Timeout - close connection
                    break

                for sock in readable:
                    try:
                        data = sock.recv(65536)
                        if not data:
                            return
                        target = upstream if sock is self.connection else self.connection
                        target.sendall(data)
                    except (BlockingIOError, socket.error):
                        continue
                    except Exception:
                        return
        finally:
            try:
                upstream.close()
            except:
                pass

    def _handle_http_method(self):
        """Handle HTTP methods (GET, POST, etc.) by forwarding through proxy."""
        for attempt in range(MAX_RETRIES):
            upstream = None
            try:
                upstream = connect_upstream()

                # Reconstruct the request with proxy auth
                request_line = f"{self.command} {self.path} {self.request_version}\r\n"

                # Build headers, adding proxy auth
                headers = f"Proxy-Authorization: {get_auth_header()}\r\n"
                for key, value in self.headers.items():
                    if key.lower() not in ("proxy-authorization", "proxy-connection"):
                        headers += f"{key}: {value}\r\n"
                headers += "Proxy-Connection: keep-alive\r\n"

                # Read request body if present
                content_length = int(self.headers.get("Content-Length", 0))
                body = self.rfile.read(content_length) if content_length > 0 else b""

                # Send request to upstream
                upstream.sendall(request_line.encode() + headers.encode() + b"\r\n" + body)

                # Read and forward response
                upstream.settimeout(60)
                response = b""

                # Read headers
                while b"\r\n\r\n" not in response and len(response) < 65536:
                    chunk = upstream.recv(8192)
                    if not chunk:
                        break
                    response += chunk

                if not response:
                    raise ConnectionError("Empty response from upstream")

                header_end = response.find(b"\r\n\r\n")
                if header_end == -1:
                    raise ConnectionError("Invalid response from upstream")

                # Check for retryable errors
                status_line = response.split(b"\r\n")[0]
                if b" 503 " in status_line or b" 502 " in status_line:
                    upstream.close()
                    if attempt < MAX_RETRIES - 1:
                        time.sleep(RETRY_DELAY * (attempt + 1))
                        continue

                # Forward the response as-is
                self.wfile.write(response)

                # Continue reading body if needed
                headers_str = response[:header_end].decode("utf-8", errors="replace").lower()

                # Check for chunked encoding or content-length
                if "transfer-encoding: chunked" in headers_str:
                    body_start = response[header_end + 4:]
                    self._forward_chunked(upstream, body_start)
                elif "content-length:" in headers_str:
                    for line in headers_str.split("\r\n"):
                        if line.startswith("content-length:"):
                            total_length = int(line.split(":")[1].strip())
                            body_so_far = len(response) - header_end - 4
                            remaining = total_length - body_so_far
                            while remaining > 0:
                                chunk = upstream.recv(min(65536, remaining))
                                if not chunk:
                                    break
                                self.wfile.write(chunk)
                                remaining -= len(chunk)
                            break

                return

            except Exception as e:
                if upstream:
                    try:
                        upstream.close()
                    except:
                        pass
                if attempt < MAX_RETRIES - 1:
                    time.sleep(RETRY_DELAY * (attempt + 1))
                    continue
                else:
                    try:
                        self.send_error(502, f"Proxy error: {e}")
                    except:
                        pass
                    return

    def _forward_chunked(self, upstream, initial_data):
        """Forward chunked transfer encoding data."""
        data = initial_data
        while True:
            # Ensure we have a complete chunk header
            while b"\r\n" not in data:
                chunk = upstream.recv(8192)
                if not chunk:
                    return
                data += chunk

            line_end = data.find(b"\r\n")
            try:
                chunk_size = int(data[:line_end], 16)
            except ValueError:
                return

            if chunk_size == 0:
                self.wfile.write(data)
                return

            # Read the full chunk
            needed = chunk_size + 2 - (len(data) - line_end - 2)
            while needed > 0:
                chunk = upstream.recv(min(65536, needed))
                if not chunk:
                    return
                data += chunk
                needed -= len(chunk)

            # Write this chunk and continue
            chunk_end = line_end + 2 + chunk_size + 2
            self.wfile.write(data[:chunk_end])
            data = data[chunk_end:]

    # Handle all HTTP methods
    do_GET = _handle_http_method
    do_POST = _handle_http_method
    do_PUT = _handle_http_method
    do_DELETE = _handle_http_method
    do_HEAD = _handle_http_method
    do_OPTIONS = _handle_http_method
    do_PATCH = _handle_http_method

class ThreadedProxyServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    print(f"Starting auth proxy on 127.0.0.1:{port}", flush=True)
    print(f"Upstream proxy: {PROXY_HOST}:{PROXY_PORT}", flush=True)
    with ThreadedProxyServer(("127.0.0.1", port), ProxyHandler) as server:
        server.serve_forever()
PROXY_SCRIPT

  nohup python3 /tmp/gradle_auth_proxy.py $LOCAL_PROXY_PORT > /tmp/gradle_proxy.log 2>&1 &

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

# Configure Gradle and Java to use local proxy
configure_gradle() {
  # Configure Gradle via properties file
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

  # Also set JAVA_TOOL_OPTIONS for any Java process that doesn't use Gradle properties
  # This is written to a file that can be sourced by the shell
  local java_opts="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=$LOCAL_PROXY_PORT"
  java_opts="$java_opts -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=$LOCAL_PROXY_PORT"
  java_opts="$java_opts -Dhttp.nonProxyHosts=localhost|127.0.0.1"

  # Export for current session
  export JAVA_TOOL_OPTIONS="$java_opts"

  # Write to profile for new shells
  local env_file="$HOME/.gradle_proxy_env"
  echo "export JAVA_TOOL_OPTIONS=\"$java_opts\"" > "$env_file"

  # Source from bashrc/zshrc if not already present
  for rc in "$HOME/.bashrc" "$HOME/.zshrc"; do
    if [ -f "$rc" ]; then
      if ! grep -q "gradle_proxy_env" "$rc" 2>/dev/null; then
        echo "[ -f \"$env_file\" ] && source \"$env_file\"" >> "$rc"
      fi
    fi
  done

  echo "✓ JAVA_TOOL_OPTIONS configured for all Java processes"
}

# Create a Gradle wrapper script with retry logic
# This handles 503 errors that happen inside the TLS tunnel
create_gradle_retry_wrapper() {
  local wrapper_script="$GRADLE_USER_HOME/gradlew-retry"

  cat > "$wrapper_script" << 'WRAPPER_EOF'
#!/bin/bash
# Gradle wrapper with automatic retry for transient network errors.
# Retries the Gradle command if it fails with 503 or connection errors.

MAX_RETRIES=5
RETRY_DELAYS=(2 5 10 20 40)

run_gradle() {
  local project_dir="${GRADLE_PROJECT_DIR:-.}"
  "$project_dir/gradlew" "$@"
}

main() {
  local attempt=0
  local exit_code

  while [ $attempt -lt $MAX_RETRIES ]; do
    attempt=$((attempt + 1))

    if [ $attempt -gt 1 ]; then
      delay=${RETRY_DELAYS[$attempt-2]:-30}
      echo ""
      echo ">>> Retry $attempt/$MAX_RETRIES after ${delay}s delay..."
      sleep $delay
      # Clear Gradle caches that might have partial downloads
      rm -rf ~/.gradle/caches/modules-2/files-2.1/.tmp 2>/dev/null
      rm -rf ~/.gradle/caches/modules-2/metadata-*/descriptors/*/.pending 2>/dev/null
    fi

    run_gradle "$@" 2>&1 | tee /tmp/gradle_output_$$.log
    exit_code=${PIPESTATUS[0]}

    if [ $exit_code -eq 0 ]; then
      rm -f /tmp/gradle_output_$$.log
      return 0
    fi

    # Check if the error is retryable (503, 502, connection errors)
    if grep -qE "(503|502|Service Unavailable|Connection refused|Connection reset|UnknownHostException|SocketException|timeout)" /tmp/gradle_output_$$.log 2>/dev/null; then
      echo ""
      echo ">>> Network error detected, will retry..."
    else
      # Non-retryable error
      rm -f /tmp/gradle_output_$$.log
      return $exit_code
    fi
  done

  echo ""
  echo ">>> Failed after $MAX_RETRIES attempts"
  rm -f /tmp/gradle_output_$$.log
  return $exit_code
}

main "$@"
WRAPPER_EOF

  chmod +x "$wrapper_script"

  # Create symlink in a location that's likely in PATH
  mkdir -p "$HOME/.local/bin"
  ln -sf "$wrapper_script" "$HOME/.local/bin/gradlew-retry"

  echo "✓ Gradle retry wrapper created (use 'gradlew-retry' or source the helper function)"

  # Also create a shell function that can be sourced
  cat >> "$HOME/.gradle_proxy_env" << 'FUNC_EOF'

# Gradle wrapper function with retry logic for transient network errors
gradlew() {
  local MAX_RETRIES=5
  local RETRY_DELAYS=(2 5 10 20 40)
  local attempt=0
  local exit_code

  while [ $attempt -lt $MAX_RETRIES ]; do
    attempt=$((attempt + 1))

    if [ $attempt -gt 1 ]; then
      delay=${RETRY_DELAYS[$attempt-2]:-30}
      echo ""
      echo ">>> Retry $attempt/$MAX_RETRIES after ${delay}s delay..."
      sleep $delay
      rm -rf ~/.gradle/caches/modules-2/files-2.1/.tmp 2>/dev/null
      rm -rf ~/.gradle/caches/modules-2/metadata-*/descriptors/*/.pending 2>/dev/null
    fi

    ./gradlew "$@" 2>&1 | tee /tmp/gradle_output_$$.log
    exit_code=${PIPESTATUS[0]}

    if [ $exit_code -eq 0 ]; then
      rm -f /tmp/gradle_output_$$.log
      return 0
    fi

    if grep -qE "(503|502|Service Unavailable|Connection refused|Connection reset|UnknownHostException|SocketException|timeout)" /tmp/gradle_output_$$.log 2>/dev/null; then
      echo ""
      echo ">>> Network error detected, will retry..."
    else
      rm -f /tmp/gradle_output_$$.log
      return $exit_code
    fi
  done

  echo ""
  echo ">>> Failed after $MAX_RETRIES attempts"
  rm -f /tmp/gradle_output_$$.log
  return $exit_code
}
FUNC_EOF
}

# Main execution
# Start proxy and configure Gradle BEFORE any Gradle operations
start_local_proxy
configure_gradle
create_gradle_retry_wrapper
download_gradle_wrapper

echo ""
echo "Gradle setup complete. You can now run: ./gradlew build"
echo ""
echo "For automatic retry on network errors, use: source ~/.gradle_proxy_env && gradlew build"
echo "Or use the standalone script: ~/.gradle/gradlew-retry build"
