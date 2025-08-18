package com.github.krepysh.branchswitcher.service

import com.github.krepysh.branchswitcher.config.SshConstants
import com.github.krepysh.branchswitcher.model.SshHost
import com.github.krepysh.branchswitcher.parser.SshConfigParser
import com.github.krepysh.branchswitcher.util.FileUtils

class SshConfigService {
    private val parser = SshConfigParser()
    
    fun getAllHosts(): List<SshHost> = parser.parseConfig()
    
    fun hasGatewayHost(): Boolean = getAllHosts().any { it.name == SshConstants.GATEWAY_HOST }
    
    fun getGatewayHost(): SshHost? = getAllHosts().find { it.name == SshConstants.GATEWAY_HOST }
    
    fun getDefaultIdentityFile(): String = getGatewayHost()?.identityFile ?: SshConstants.DEFAULT_IDENTITY_FILE
    
    fun addGatewayHost(identityFile: String) {
        val configFile = FileUtils.getSshConfigFile()
        val gatewayConfig = buildGatewayConfig(identityFile)
        configFile.appendText(gatewayConfig)
    }
    
    private fun buildGatewayConfig(identityFile: String): String = """
        
        Host ${SshConstants.GATEWAY_HOST}
            HostName ${SshConstants.GATEWAY_HOSTNAME}
            User ${SshConstants.GATEWAY_USER}
            IdentityFile $identityFile
        
    """.trimIndent()
}