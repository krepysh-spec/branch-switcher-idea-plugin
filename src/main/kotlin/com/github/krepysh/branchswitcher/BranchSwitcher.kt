package com.github.krepysh.branchswitcher

import com.github.krepysh.branchswitcher.service.SshConfigService

/**
 * Main facade class for BranchSwitcher library
 */
class BranchSwitcher {
    private val sshConfigService = SshConfigService()
    
    fun getSshHosts() = sshConfigService.getAllHosts()
    
    fun hasGatewayConfigured() = sshConfigService.hasGatewayHost()
    
    fun setupGateway(identityFile: String? = null) {
        val keyFile = identityFile ?: sshConfigService.getDefaultIdentityFile()
        if (!hasGatewayConfigured()) {
            sshConfigService.addGatewayHost(keyFile)
        }
    }
}