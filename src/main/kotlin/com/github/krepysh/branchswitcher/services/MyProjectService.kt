package com.github.krepysh.branchswitcher.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.krepysh.branchswitcher.parser.SshConfigParser
import com.github.krepysh.branchswitcher.model.SshHost

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {
    private val sshParser = SshConfigParser()

    init {
        thisLogger().info("Branch Switcher initialized for project: ${project.name}")
    }

    fun getSshHosts(): List<SshHost> = sshParser.parseConfig()
}