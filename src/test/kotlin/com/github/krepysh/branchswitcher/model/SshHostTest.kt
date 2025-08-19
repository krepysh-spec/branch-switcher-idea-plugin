package com.github.krepysh.branchswitcher.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SshHostTest {
    
    @Test
    fun `should create SSH host with basic properties`() {
        val host = SshHost(
            name = "test-host",
            hostname = "example.com",
            user = "testuser"
        )
        
        assertEquals("test-host", host.name)
        assertEquals("example.com", host.hostname)
        assertEquals("testuser", host.user)
    }
    
    @Test
    fun `should handle optional properties`() {
        val host = SshHost(
            name = "test-host",
            hostname = "example.com",
            user = "testuser",
            port = 2222,
            identityFile = "~/.ssh/id_rsa"
        )
        
        assertEquals(2222, host.port)
        assertEquals("~/.ssh/id_rsa", host.identityFile)
    }
}