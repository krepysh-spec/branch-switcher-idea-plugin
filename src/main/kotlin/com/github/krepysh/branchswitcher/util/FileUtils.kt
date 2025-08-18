package com.github.krepysh.branchswitcher.util

import java.io.File

object FileUtils {
    fun getSshConfigFile(): File = File(System.getProperty("user.home"), ".ssh/config")
    
    fun ensureFileExists(file: File): Boolean {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            return file.createNewFile()
        }
        return true
    }
}