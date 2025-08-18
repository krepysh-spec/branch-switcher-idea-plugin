package com.github.krepysh.branchswitcher.parser

import com.github.krepysh.branchswitcher.model.SshHost
import com.github.krepysh.branchswitcher.util.FileUtils

class SshConfigParser {
    
    fun parseConfig(): List<SshHost> {
        val hosts = mutableListOf<SshHost>()
        
        // Parse main config
        hosts.addAll(parseConfigFile(FileUtils.getSshConfigFile()))
        
        // Parse conf.d files
        hosts.addAll(parseConfDFiles())
        
        return hosts
    }
    
    private fun parseConfigFile(configFile: java.io.File): List<SshHost> {
        if (!configFile.exists()) return emptyList()
        
        val hosts = mutableListOf<SshHost>()
        var currentHost: String? = null
        val currentProperties = mutableMapOf<String, String>()
        
        configFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("Include")) return@forEach
            
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
    
    private fun parseConfDFiles(): List<SshHost> {
        val confDDir = FileUtils.getSshConfDDir()
        if (!confDDir.exists()) return emptyList()
        
        val hosts = mutableListOf<SshHost>()
        
        confDDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            projectDir.listFiles()?.filter { it.isFile }?.forEach { hostFile ->
                hosts.addAll(parseConfigFile(hostFile))
            }
        }
        
        return hosts
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