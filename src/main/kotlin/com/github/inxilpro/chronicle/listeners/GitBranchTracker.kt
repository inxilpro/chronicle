package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.BranchChangedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Tracks Git branch changes using defensive reflection-based access
 * to the git4idea plugin's GitRepository.GIT_REPO_CHANGE topic.
 *
 * This implementation degrades gracefully if git4idea is unavailable
 * or the API changes in future IDE versions.
 */
class GitBranchTracker(
    private val project: Project
) : Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    private var isRegistered = false

    fun tryRegister() {
        if (isRegistered) return

        try {
            val gitRepositoryClass = Class.forName("git4idea.repo.GitRepository")
            val topicField = gitRepositoryClass.getDeclaredField("GIT_REPO_CHANGE")
            topicField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val topic = topicField.get(null) as Topic<Any>

            val listenerClass = Class.forName("git4idea.repo.GitRepositoryChangeListener")

            val handler = InvocationHandler { _, method, args ->
                when (method.name) {
                    "repositoryChanged" -> {
                        handleRepositoryChanged(args?.getOrNull(0))
                    }
                    "equals" -> this == args?.getOrNull(0)
                    "hashCode" -> this.hashCode()
                    "toString" -> "GitBranchTracker.Listener"
                }
                null
            }

            val listener = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(listenerClass),
                handler
            )

            val connection = project.messageBus.connect(this)
            connection.subscribe(topic, listener)

            isRegistered = true
            thisLogger().info("Git branch tracking registered successfully")

        } catch (e: ClassNotFoundException) {
            thisLogger().info("Git branch tracking unavailable: git4idea plugin not found")
        } catch (e: NoSuchFieldException) {
            thisLogger().warn("Git branch tracking unavailable: GIT_REPO_CHANGE field not found")
        } catch (e: Exception) {
            thisLogger().warn("Git branch tracking unavailable: ${e.message}")
        }
    }

    private fun handleRepositoryChanged(repository: Any?) {
        if (repository == null) return

        try {
            val repoClass = repository.javaClass

            val rootPath = extractRootPath(repository, repoClass)
            val branchName = extractBranchName(repository, repoClass)
            val stateName = extractStateName(repository, repoClass)

            transcriptService.log(
                BranchChangedEvent(
                    repository = rootPath ?: "unknown",
                    branch = branchName,
                    state = stateName ?: "UNKNOWN"
                )
            )
        } catch (e: Exception) {
            thisLogger().debug("Failed to extract git repository info: ${e.message}")
        }
    }

    private fun extractRootPath(repository: Any, repoClass: Class<*>): String? {
        return try {
            val getRootMethod = repoClass.getMethod("getRoot")
            val root = getRootMethod.invoke(repository)
            val getPathMethod = root.javaClass.getMethod("getPath")
            getPathMethod.invoke(root) as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun extractBranchName(repository: Any, repoClass: Class<*>): String? {
        return try {
            val getCurrentBranchMethod = repoClass.getMethod("getCurrentBranch")
            val branch = getCurrentBranchMethod.invoke(repository)
            if (branch != null) {
                val getNameMethod = branch.javaClass.getMethod("getName")
                getNameMethod.invoke(branch) as? String
            } else {
                val getCurrentRevisionMethod = repoClass.getMethod("getCurrentRevision")
                val revision = getCurrentRevisionMethod.invoke(repository) as? String
                revision?.take(8)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractStateName(repository: Any, repoClass: Class<*>): String? {
        return try {
            val getStateMethod = repoClass.getMethod("getState")
            val state = getStateMethod.invoke(repository)
            val nameMethod = state.javaClass.getMethod("name")
            nameMethod.invoke(state) as? String
        } catch (e: Exception) {
            null
        }
    }

    override fun dispose() {
        isRegistered = false
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): GitBranchTracker {
            val tracker = GitBranchTracker(project)
            tracker.tryRegister()
            Disposer.register(parentDisposable, tracker)
            return tracker
        }
    }
}
