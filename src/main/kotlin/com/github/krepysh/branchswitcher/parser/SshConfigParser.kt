package com.github.krepysh.branchswitcher.parser

import com.github.krepysh.branchswitcher.model.SshHost
import java.io.File

class SshConfigParser {
    
    companion object {
        private const val GATEWAY_HOST = "gatesftp2"
        private const val GATEWAY_HOSTNAME = "23.109.14.108"
        private const val GATEWAY_USER = "gateway"
        private const val GATEWAY_IDENTITY_FILE = "~/.ssh/id_rsa"
    }
    
    fun parseConfig(): List<SshHost> {
        val configFile = File(System.getProperty("user.home"), ".ssh/config")
        if (!configFile.exists()) return emptyList()
        
        val hosts = mutableListOf<SshHost>()
        var currentHost: String? = null
        val currentProperties = mutableMapOf<String, String>()
        
        configFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            
            val parts = trimmed.split("\\s+".toRegex(), 2)
            if (parts.size < 2) return@forEach
            
            val key = parts[0].lowercase()
            val value = parts[1]
            
            when (key) {
                "host" -> {
                    currentHost?.let { 
                        hosts.add(createHost(it, currentProperties))
                        currentProperties.clear()
                    }
                    currentHost = value
                }
                else -> currentProperties[key] = value
            }
        }
        
        currentHost?.let { 
            hosts.add(createHost(it, currentProperties))
        }
        
        return hosts
    }
    
    fun hasGatewayHost(): Boolean {
        val hosts = parseConfig()
        return hosts.any { it.name == GATEWAY_HOST }
    }
    
    fun addGatewayHost(identityFile: String) {
        val configFile = File(System.getProperty("user.home"), ".ssh/config")
        val gatewayConfig = "\n\nHost $GATEWAY_HOST\n" +
                "    HostName $GATEWAY_HOSTNAME\n" +
                "    User $GATEWAY_USER\n" +
                "    IdentityFile $identityFile\n"
        
        configFile.appendText(gatewayConfig)
    }
    

    
    fun getDefaultIdentityFile(): String {
        val hosts = parseConfig()
        val gatewayHost = hosts.find { it.name == GATEWAY_HOST }
        return gatewayHost?.identityFile ?: GATEWAY_IDENTITY_FILE
    }
    
    private fun createHost(name: String, properties: Map<String, String>): SshHost {
        return SshHost(
            name = name,
            hostname = properties["hostname"],
            user = properties["user"],
            port = properties["port"]?.toIntOrNull(),
            identityFile = properties["identityfile"],
            proxyCommand = properties["proxycommand"],
            otherProperties = properties.filterKeys { 
                it !in setOf("hostname", "user", "port", "identityfile", "proxycommand")
            }
        )
    }
}