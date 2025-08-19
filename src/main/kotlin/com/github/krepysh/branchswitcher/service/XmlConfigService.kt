package com.github.krepysh.branchswitcher.service

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

class XmlConfigService {
    
    fun createSshConfigForProjects(hostName: String, hostname: String, user: String, port: Int, identityFile: String, selectedProject: String) {
        val projectsDir = File(System.getProperty("user.home"), "Projects")
        if (!projectsDir.exists()) return
        
        projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val ideaDir = File(projectDir, ".idea")
            if (!ideaDir.exists()) ideaDir.mkdirs()
            
            val sshFile = File(ideaDir, "sshConfigs.xml")
            addOrUpdateSshConfig(sshFile, hostName, hostname, user, port, identityFile, selectedProject)
        }
    }
    
    fun createDeploymentConfigForProjects(hostName: String, selectedProject: String) {
        val projectsDir = File(System.getProperty("user.home"), "Projects")
        if (!projectsDir.exists()) return
        
        projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val ideaDir = File(projectDir, ".idea")
            if (!ideaDir.exists()) ideaDir.mkdirs()
            
            val webServersFile = File(ideaDir, "webServers.xml")
            addOrUpdateWebServer(webServersFile, hostName, selectedProject)
        }
    }
    
    fun removeSshConfigFromProjects(hostName: String) {
        val projectsDir = File(System.getProperty("user.home"), "Projects")
        if (!projectsDir.exists()) return
        
        projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val sshFile = File(projectDir, ".idea/sshConfigs.xml")
            if (sshFile.exists()) {
                removeSshConfigFromXml(sshFile, hostName)
            }
        }
    }
    
    fun removeDeploymentConfigFromProjects(hostName: String) {
        val projectsDir = File(System.getProperty("user.home"), "Projects")
        if (!projectsDir.exists()) return
        
        projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val webServersFile = File(projectDir, ".idea/webServers.xml")
            if (webServersFile.exists()) {
                removeWebServerFromXml(webServersFile, hostName)
            }
        }
    }
    
    private fun addOrUpdateSshConfig(xmlFile: File, hostName: String, hostname: String, user: String, port: Int, identityFile: String, selectedProject: String) {
        try {
            val doc = if (xmlFile.exists()) {
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                builder.parse(xmlFile)
            } else {
                createEmptySshDocument()
            }
            
            // Remove existing host if present
            val configs = doc.getElementsByTagName("sshConfig")
            for (i in configs.length - 1 downTo 0) {
                val config = configs.item(i) as Element
                if (config.getAttribute("id") == hostName) {
                    config.parentNode.removeChild(config)
                }
            }
            
            // Add new host
            val configsElement = doc.getElementsByTagName("configs").item(0) as Element
            val sshConfig = doc.createElement("sshConfig")
            sshConfig.setAttribute("host", hostname)
            sshConfig.setAttribute("id", hostName)
            if (identityFile.isNotEmpty()) {
                sshConfig.setAttribute("keyPath", "\$USER_HOME\$/.ssh/id_rsa")
            }
            sshConfig.setAttribute("port", port.toString())
            sshConfig.setAttribute("customName", selectedProject)
            sshConfig.setAttribute("nameFormat", "CUSTOM")
            sshConfig.setAttribute("username", user)
            sshConfig.setAttribute("useOpenSSHConfig", "true")
            
            val option = doc.createElement("option")
            option.setAttribute("name", "customName")
            option.setAttribute("value", selectedProject)
            sshConfig.appendChild(option)
            
            configsElement.appendChild(sshConfig)
            
            saveDocument(doc, xmlFile)
        } catch (e: Exception) {
            println("Error creating SSH config: ${e.message}")
        }
    }
    
    private fun addOrUpdateWebServer(xmlFile: File, hostName: String, selectedProject: String) {
        try {
            val doc = if (xmlFile.exists()) {
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                builder.parse(xmlFile)
            } else {
                createEmptyWebServersDocument()
            }
            
            val serversElement = doc.getElementsByTagName("option").item(0) as Element
            val webServer = doc.createElement("webServer")
            webServer.setAttribute("id", java.util.UUID.randomUUID().toString())
            webServer.setAttribute("name", selectedProject)
            
            val fileTransfer = doc.createElement("fileTransfer")
            fileTransfer.setAttribute("rootFolder", "/home/dev/backend")
            fileTransfer.setAttribute("accessType", "SFTP")
            fileTransfer.setAttribute("host", hostName)
            fileTransfer.setAttribute("port", "22")
            fileTransfer.setAttribute("sshConfigId", java.util.UUID.randomUUID().toString())
            fileTransfer.setAttribute("sshConfig", selectedProject)
            fileTransfer.setAttribute("keyPair", "true")
            
            val advancedOptions = doc.createElement("advancedOptions")
            val advancedOptionsInner = doc.createElement("advancedOptions")
            advancedOptionsInner.setAttribute("dataProtectionLevel", "Private")
            advancedOptionsInner.setAttribute("keepAliveTimeout", "0")
            advancedOptionsInner.setAttribute("passiveMode", "true")
            advancedOptionsInner.setAttribute("shareSSLContext", "true")
            advancedOptionsInner.setAttribute("isUseRsync", "true")
            
            advancedOptions.appendChild(advancedOptionsInner)
            fileTransfer.appendChild(advancedOptions)
            webServer.appendChild(fileTransfer)
            serversElement.appendChild(webServer)
            
            saveDocument(doc, xmlFile)
        } catch (e: Exception) {
            println("Error creating webServers.xml: ${e.message}")
        }
    }
    
    private fun removeSshConfigFromXml(xmlFile: File, hostName: String) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xmlFile)
            
            val configs = doc.getElementsByTagName("sshConfig")
            for (i in configs.length - 1 downTo 0) {
                val config = configs.item(i) as Element
                if (config.getAttribute("id") == hostName) {
                    config.parentNode.removeChild(config)
                }
            }
            
            saveDocument(doc, xmlFile)
        } catch (e: Exception) {
            xmlFile.writeText("")
        }
    }
    
    private fun removeWebServerFromXml(xmlFile: File, hostName: String) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xmlFile)
            
            val webServers = doc.getElementsByTagName("webServer")
            for (i in webServers.length - 1 downTo 0) {
                val webServer = webServers.item(i) as Element
                val fileTransfer = webServer.getElementsByTagName("fileTransfer").item(0) as? Element
                if (fileTransfer?.getAttribute("host") == hostName) {
                    webServer.parentNode.removeChild(webServer)
                }
            }
            
            saveDocument(doc, xmlFile)
        } catch (e: Exception) {
            println("Error removing from webServers.xml: ${e.message}")
        }
    }
    
    private fun createEmptySshDocument(): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val project = doc.createElement("project")
        project.setAttribute("version", "4")
        doc.appendChild(project)
        
        val component = doc.createElement("component")
        component.setAttribute("name", "SshConfigs")
        project.appendChild(component)
        
        val configs = doc.createElement("configs")
        component.appendChild(configs)
        
        return doc
    }
    
    private fun createEmptyWebServersDocument(): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val project = doc.createElement("project")
        project.setAttribute("version", "4")
        doc.appendChild(project)
        
        val component = doc.createElement("component")
        component.setAttribute("name", "WebServers")
        project.appendChild(component)
        
        val option = doc.createElement("option")
        option.setAttribute("name", "servers")
        component.appendChild(option)
        
        return doc
    }
    
    private fun saveDocument(doc: Document, file: File) {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.transform(DOMSource(doc), StreamResult(file))
    }
}