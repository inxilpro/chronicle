package com.github.inxilpro.chronicle.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

// Uses reflection to get the current git branch without compile-time dependency on git4idea
object GitBranchHelper {

    fun getCurrentBranch(project: Project): String? {
        return try {
            val gitRepositoryManagerClass = Class.forName("git4idea.repo.GitRepositoryManager")
            val getInstanceMethod = gitRepositoryManagerClass.getMethod("getInstance", Project::class.java)
            val manager = getInstanceMethod.invoke(null, project)

            val getRepositoriesMethod = manager.javaClass.getMethod("getRepositories")
            @Suppress("UNCHECKED_CAST")
            val repositories = getRepositoriesMethod.invoke(manager) as List<Any>

            if (repositories.isEmpty()) {
                return null
            }

            // Use the first repository (most projects have one)
            val repository = repositories.first()
            extractBranchName(repository)
        } catch (e: ClassNotFoundException) {
            thisLogger().debug("Git branch helper: git4idea plugin not found")
            null
        } catch (e: Exception) {
            thisLogger().debug("Git branch helper: failed to get branch: ${e.message}")
            null
        }
    }

    private fun extractBranchName(repository: Any): String? {
        return try {
            val getCurrentBranchMethod = repository.javaClass.getMethod("getCurrentBranch")
            val branch = getCurrentBranchMethod.invoke(repository)
            if (branch != null) {
                val getNameMethod = branch.javaClass.getMethod("getName")
                getNameMethod.invoke(branch) as? String
            } else {
                // Detached HEAD - use short revision
                val getCurrentRevisionMethod = repository.javaClass.getMethod("getCurrentRevision")
                val revision = getCurrentRevisionMethod.invoke(repository) as? String
                revision?.take(8)
            }
        } catch (e: Exception) {
            null
        }
    }
}
