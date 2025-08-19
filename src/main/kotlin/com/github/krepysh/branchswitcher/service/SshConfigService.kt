package com.github.krepysh.branchswitcher.service

import com.github.krepysh.branchswitcher.config.SshConstants
import com.github.krepysh.branchswitcher.model.SshHost
import com.github.krepysh.branchswitcher.parser.SshConfigParser
import com.github.krepysh.branchswitcher.util.FileUtils

class SshConfigService {
    private val parser = SshConfigParser()
    
    fun getAllHosts(): List<SshHost> = parser.parseConfig()
    
    fun removeHost(hostName: String) {
        // Try to find and remove from project files first
        val confDDir = FileUtils.getSshConfDDir()
        var removed = false
        
        if (confDDir.exists()) {
            confDDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
                val hostFile = java.io.File(projectDir, hostName)
                if (hostFile.exists()) {
                    hostFile.delete()
                    removed = true
                }
            }
        }
        
        // If not found in project files, try main config
        if (!removed) {
            removeHostFromMainConfig(hostName)
        }
    }
    
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
    
    fun removeHostFromMainConfig(hostName: String) {
        val configFile = FileUtils.getSshConfigFile()
        if (!configFile.exists()) return
        
        val lines = configFile.readLines().toMutableList()
        val newLines = mutableListOf<String>()
        var skipHost = false
        
        for (line in lines) {
            val trimmed = line.trim()
            
            if (trimmed.startsWith("Host ") && trimmed.substringAfter("Host ").trim() == hostName) {
                skipHost = true
                continue
            }
            
            if (skipHost && trimmed.startsWith("Host ")) {
                skipHost = false
            }
            
            if (!skipHost) {
                newLines.add(line)
            }
        }
        
        configFile.writeText(newLines.joinToString("\n"))
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