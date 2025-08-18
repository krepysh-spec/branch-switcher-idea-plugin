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
    
    fun addProjectHost(projectName: String, hostName: String, hostname: String, user: String, port: Int?, identityFile: String?) {
        FileUtils.ensureIncludeInMainConfig()
        
        val hostFile = FileUtils.getProjectHostFile(projectName, hostName)
        FileUtils.ensureFileExists(hostFile)
        
        val hostConfig = buildHostConfig(hostName, hostname, user, port, identityFile)
        hostFile.writeText(hostConfig)
    }
    
    fun removeProjectHost(projectName: String, hostName: String) {
        val hostFile = FileUtils.getProjectHostFile(projectName, hostName)
        if (hostFile.exists()) {
            hostFile.delete()
        }
    }
    
    private fun buildGatewayConfig(identityFile: String): String = """
        
        Host ${SshConstants.GATEWAY_HOST}
            HostName ${SshConstants.GATEWAY_HOSTNAME}
            User ${SshConstants.GATEWAY_USER}
            IdentityFile $identityFile
        
    """.trimIndent()
    
    private fun buildHostConfig(hostName: String, hostname: String, user: String, port: Int?, identityFile: String?): String {
        val config = StringBuilder()
        config.append("Host $hostName\n")
        config.append("    HostName $hostname\n")
        config.append("    User $user\n")
        port?.let { config.append("    Port $it\n") }
        identityFile?.let { config.append("    IdentityFile $it\n") }
        config.append("    ProxyJump ${SshConstants.GATEWAY_HOST}\n")
        return config.toString()
    }
}