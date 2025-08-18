package com.github.krepysh.branchswitcher.util

import com.github.krepysh.branchswitcher.config.SshConstants
import java.io.File

object FileUtils {
    fun getSshConfigFile(): File = File(System.getProperty("user.home"), SshConstants.SSH_CONFIG_PATH)
    
    fun getSshConfDDir(): File = File(System.getProperty("user.home"), SshConstants.SSH_CONF_D_PATH)
    
    fun getProjectHostFile(projectName: String, hostName: String): File {
        val projectDir = File(getSshConfDDir(), projectName.lowercase())
        return File(projectDir, hostName)
    }
    
    fun ensureFileExists(file: File): Boolean {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            return file.createNewFile()
        }
        return true
    }
    
    fun ensureIncludeInMainConfig() {
        val configFile = getSshConfigFile()
        val includeStatement = "Include ${SshConstants.SSH_CONF_D_PATH}/*/*"
        
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            configFile.writeText("$includeStatement\n")
        } else {
            val content = configFile.readText()
            if (!content.contains(includeStatement)) {
                configFile.writeText("$includeStatement\n$content")
            }
        }
    }
}