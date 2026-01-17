package com.github.inxilpro.chronicle.listeners

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GitBranchHelperTest : BasePlatformTestCase() {

    fun testGetCurrentBranchReturnsNullWhenGitPluginUnavailable() {
        // In tests, git4idea plugin is not available, so this should return null gracefully
        val branch = GitBranchHelper.getCurrentBranch(project)
        assertNull(branch)
    }
}
