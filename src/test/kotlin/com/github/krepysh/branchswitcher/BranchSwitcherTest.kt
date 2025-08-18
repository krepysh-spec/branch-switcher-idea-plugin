package com.github.krepysh.branchswitcher

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BranchSwitcherTest {
    
    @Test
    fun `should create BranchSwitcher instance`() {
        val branchSwitcher = BranchSwitcher()
        assertNotNull(branchSwitcher)
    }
    
    @Test
    fun `should get SSH hosts`() {
        val branchSwitcher = BranchSwitcher()
        val hosts = branchSwitcher.getSshHosts()
        assertNotNull(hosts)
    }
}