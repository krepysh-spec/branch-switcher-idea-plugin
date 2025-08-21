package com.github.krepysh.branchswitcher.service

import com.github.krepysh.branchswitcher.config.XmlConstants
import com.intellij.openapi.application.ApplicationManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

class XmlConfigService {
    
    private data class ConfigName(val hostName: String, val projectName: String)
    
    private val projectsDir = File(System.getProperty("user.home"), "Projects")
    
    fun createSshConfigForProjects(hostName: String, hostname: String, user: String, port: Int, identityFile: String, selectedProject: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val configName = generateUniqueConfigName(hostName, selectedProject)
            processAllProjects { projectDir ->
                val sshFile = File(projectDir, "${XmlConstants.IDEA_DIR}/${XmlConstants.SSH_CONFIGS_XML}")
                addOrUpdateSshConfig(sshFile, hostName, hostname, user, port, identityFile, configName.projectName)
            }
        }
    }
    
    fun createDeploymentConfigForProjects(hostName: String, selectedProject: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val configName = generateUniqueConfigName(hostName, selectedProject)
            processAllProjects { projectDir ->
                val webServersFile = File(projectDir, "${XmlConstants.IDEA_DIR}/${XmlConstants.WEB_SERVERS_XML}")
                val deploymentFile = File(projectDir, "${XmlConstants.IDEA_DIR}/${XmlConstants.DEPLOYMENT_XML}")
                
                addOrUpdateWebServer(webServersFile, hostName, configName.projectName)
                addOrUpdateDeploymentMapping(deploymentFile, configName.projectName)
            }
        }
    }
    
    fun removeSshConfigFromProjects(hostName: String) {
        processAllProjects { projectDir ->
            val sshFile = File(projectDir, "${XmlConstants.IDEA_DIR}/${XmlConstants.SSH_CONFIGS_XML}")
            if (sshFile.exists()) removeSshConfigFromXml(sshFile, hostName)
        }
    }
    
    fun removeDeploymentConfigFromProjects(hostName: String) {
        processAllProjects { projectDir ->
            val webServersFile = File(projectDir, "${XmlConstants.IDEA_DIR}/${XmlConstants.WEB_SERVERS_XML}")
            val deploymentFile = File(projectDir, "${XmlConstants.IDEA_DIR}/${XmlConstants.DEPLOYMENT_XML}")
            
            if (webServersFile.exists()) removeWebServerFromXml(webServersFile, hostName)
            if (deploymentFile.exists()) removeDeploymentMappingFromXml(deploymentFile, hostName)
        }
    }
    
    private fun generateUniqueConfigName(hostName: String, selectedProject: String): ConfigName {
        val existingNames = getAllExistingConfigNames()
        
        var uniqueName = selectedProject
        var counter = 2
        while (existingNames.contains(uniqueName)) {
            uniqueName = "${selectedProject}_${counter}"
            counter++
        }
        
        return ConfigName(hostName, uniqueName)
    }
    
    private fun getAllExistingConfigNames(): Set<String> {
        val existingNames = mutableSetOf<String>()
        
        processAllProjects { projectDir ->
            existingNames.addAll(extractSshConfigNames(projectDir))
            existingNames.addAll(extractWebServerNames(projectDir))
        }
        
        return existingNames
    }
    
    private fun extractSshConfigNames(projectDir: File): Set<String> {
        val sshFile = File(projectDir, "${XmlConstants.IDEA_DIR}/${XmlConstants.SSH_CONFIGS_XML}")
        if (!sshFile.exists() || sshFile.length() == 0L) return emptySet()
        
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sshFile)
            val configs = doc.getElementsByTagName("sshConfig")
            if (configs.length == 0) return emptySet()
            
            configs.asSequence()
                .map { it as Element }
                .map { it.getAttribute("customName") }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    private fun extractWebServerNames(projectDir: File): Set<String> {
        val webServersFile = File(projectDir, "${XmlConstants.IDEA_DIR}/${XmlConstants.WEB_SERVERS_XML}")
        if (!webServersFile.exists() || webServersFile.length() == 0L) return emptySet()
        
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(webServersFile)
            val webServers = doc.getElementsByTagName("webServer")
            if (webServers.length == 0) return emptySet()
            
            webServers.asSequence()
                .map { it as Element }
                .map { it.getAttribute("name") }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    private fun processAllProjects(action: (File) -> Unit) {
        if (!projectsDir.exists()) return
        
        projectsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { projectDir ->
                val ideaDir = File(projectDir, XmlConstants.IDEA_DIR)
                if (!ideaDir.exists()) ideaDir.mkdirs()
                action(projectDir)
            }
    }
    
    private fun addOrUpdateSshConfig(xmlFile: File, hostName: String, hostname: String, user: String, port: Int, identityFile: String, configName: String) {
        val doc = if (xmlFile.exists()) parseXmlDocument(xmlFile) else createEmptySshDocument()
        
        removeExistingConfig(doc, "sshConfig", "id", hostName)
        
        val configsElement = doc.getElementsByTagName("configs").item(0) as Element
        val sshConfig = doc.createElement("sshConfig").apply {
            setAttribute("host", hostname)
            setAttribute("id", hostName)
            setAttribute("port", port.toString())
            setAttribute("customName", configName)
            setAttribute("nameFormat", "CUSTOM")
            setAttribute("username", user)
            setAttribute("useOpenSSHConfig", "true")
            if (identityFile.isNotEmpty()) setAttribute("keyPath", XmlConstants.DEFAULT_IDENTITY_PATH)
            
            appendChild(doc.createElement("option").apply {
                setAttribute("name", "customName")
                setAttribute("value", configName)
            })
        }
        
        configsElement.appendChild(sshConfig)
        saveDocument(doc, xmlFile)
    }
    
    private fun addOrUpdateWebServer(xmlFile: File, hostName: String, configName: String) {
        val doc = if (xmlFile.exists()) parseXmlDocument(xmlFile) else createEmptyWebServersDocument()
        
        val serversElement = doc.getElementsByTagName("option").item(0) as Element
        val webServer = doc.createElement("webServer").apply {
            setAttribute("id", java.util.UUID.randomUUID().toString())
            setAttribute("name", configName)
            
            appendChild(doc.createElement("fileTransfer").apply {
                setAttribute("rootFolder", XmlConstants.DEFAULT_REMOTE_PATH)
                setAttribute("accessType", "SFTP")
                setAttribute("host", hostName)
                setAttribute("port", XmlConstants.DEFAULT_SSH_PORT)
                setAttribute("sshConfigId", java.util.UUID.randomUUID().toString())
                setAttribute("sshConfig", configName)
                setAttribute("keyPair", "true")
                
                appendChild(doc.createElement("advancedOptions").apply {
                    appendChild(doc.createElement("advancedOptions").apply {
                        setAttribute("dataProtectionLevel", "Private")
                        setAttribute("keepAliveTimeout", "0")
                        setAttribute("passiveMode", "true")
                        setAttribute("shareSSLContext", "true")
                    })
                })
            })
        }
        
        serversElement.appendChild(webServer)
        saveDocument(doc, xmlFile)
    }
    
    private fun addOrUpdateDeploymentMapping(xmlFile: File, configName: String) {
        val doc = if (xmlFile.exists()) parseXmlDocument(xmlFile) else createEmptyDeploymentDocument()
        
        val component = doc.getElementsByTagName("component").item(0) as Element
        component.setAttribute("serverName", configName)
        
        val serverDataElement = doc.getElementsByTagName("serverData").item(0) as? Element
            ?: doc.createElement("serverData").also { component.appendChild(it) }
        
        removeExistingConfig(doc, "paths", "name", configName)
        
        val paths = doc.createElement("paths").apply {
            setAttribute("name", configName)
            appendChild(doc.createElement("serverdata").apply {
                appendChild(doc.createElement("mappings").apply {
                    appendChild(doc.createElement("mapping").apply {
                        setAttribute("deploy", "/")
                        setAttribute("local", "\$PROJECT_DIR\$")
                        setAttribute("web", XmlConstants.DEFAULT_REMOTE_PATH)
                    })
                })
            })
        }
        
        serverDataElement.appendChild(paths)
        saveDocument(doc, xmlFile)
    }
    
    private fun removeSshConfigFromXml(xmlFile: File, hostName: String) {
        try {
            val doc = parseXmlDocument(xmlFile)
            val configs = doc.getElementsByTagName("sshConfig")
            for (i in configs.length - 1 downTo 0) {
                val config = configs.item(i) as Element
                if (config.getAttribute("id") == hostName || config.getAttribute("customName").contains(hostName)) {
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
            val doc = parseXmlDocument(xmlFile)
            val webServers = doc.getElementsByTagName("webServer")
            for (i in webServers.length - 1 downTo 0) {
                val webServer = webServers.item(i) as Element
                val fileTransfer = webServer.getElementsByTagName("fileTransfer").item(0) as? Element
                if (fileTransfer?.getAttribute("host") == hostName) {
                    webServer.parentNode.removeChild(webServer)
                }
            }
            
            val remaining = doc.getElementsByTagName("webServer").length
            if (remaining == 0) {
                xmlFile.delete()
            } else {
                saveDocument(doc, xmlFile)
            }
        } catch (e: Exception) {
            // Якщо файл пошкоджено — видалити, щоб не залишати зламаний XML
            xmlFile.delete()
        }
    }
    
    private fun removeDeploymentMappingFromXml(xmlFile: File, hostName: String) {
        try {
            val doc = parseXmlDocument(xmlFile)
            removeExistingConfig(doc, "paths", "name", hostName)
            saveDocument(doc, xmlFile)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun parseXmlDocument(file: File): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    
    private fun removeExistingConfig(doc: Document, tagName: String, attributeName: String, attributeValue: String) {
        val elements = doc.getElementsByTagName(tagName)
        for (i in elements.length - 1 downTo 0) {
            val element = elements.item(i) as Element
            if (element.getAttribute(attributeName) == attributeValue) {
                element.parentNode.removeChild(element)
            }
        }
    }
    
    private fun createEmptySshDocument(): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().apply {
            appendChild(createElement("project").apply {
                setAttribute("version", "4")
                appendChild(createElement("component").apply {
                    setAttribute("name", "SshConfigs")
                    appendChild(createElement("configs"))
                })
            })
        }
    
    private fun createEmptyWebServersDocument(): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().apply {
            appendChild(createElement("project").apply {
                setAttribute("version", "4")
                appendChild(createElement("component").apply {
                    setAttribute("name", "WebServers")
                    appendChild(createElement("option").apply {
                        setAttribute("name", "servers")
                    })
                })
            })
        }
    
    private fun createEmptyDeploymentDocument(): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().apply {
            appendChild(createElement("project").apply {
                setAttribute("version", "4")
                appendChild(createElement("component").apply {
                    setAttribute("name", "PublishConfigData")
                    setAttribute("remoteFilesAllowedToDisappearOnAutoupload", "false")
                    setAttribute("confirmBeforeUploading", "false")
                    setAttribute("serverName", "")
                    appendChild(createElement("option").apply {
                        setAttribute("name", "confirmBeforeUploading")
                        setAttribute("value", "false")
                    })
                    appendChild(createElement("serverData"))
                })
            })
        }
    
    private fun saveDocument(doc: Document, file: File) {
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "false")
            setOutputProperty(javax.xml.transform.OutputKeys.STANDALONE, "no")
        }.transform(DOMSource(doc), StreamResult(file))
    }
    
    private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> =
        (0 until length).asSequence().map { item(it) }
}