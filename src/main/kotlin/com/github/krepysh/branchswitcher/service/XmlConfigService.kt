package com.github.krepysh.branchswitcher.service

import com.intellij.openapi.application.ApplicationManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

class XmlConfigService {
    
    private var cachedProjectName: String? = null
    private var cachedBaseName: String? = null
    
    private fun getUniqueConfigName(baseName: String, currentProject: File): String {
        if (cachedBaseName == baseName && cachedProjectName != null) {
            return cachedProjectName!!
        }
        val projectsDir = File(System.getProperty("user.home"), "Projects")
        val allProjects = projectsDir.listFiles()?.filter { it.isDirectory } ?: return baseName
        
        val existingNames = mutableSetOf<String>()
        
        allProjects.forEach { project ->
            if (project.absolutePath == currentProject.absolutePath) return@forEach
            // Check SSH configs
            val sshFile = File(project, ".idea/sshConfigs.xml")
            if (sshFile.exists()) {
                try {
                    val factory = DocumentBuilderFactory.newInstance()
                    val builder = factory.newDocumentBuilder()
                    val doc = builder.parse(sshFile)
                    
                    val configs = doc.getElementsByTagName("sshConfig")
                    for (i in 0 until configs.length) {
                        val config = configs.item(i) as Element
                        existingNames.add(config.getAttribute("customName"))
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
            
            // Check WebServers configs
            val webServersFile = File(project, ".idea/webServers.xml")
            if (webServersFile.exists()) {
                try {
                    val factory = DocumentBuilderFactory.newInstance()
                    val builder = factory.newDocumentBuilder()
                    val doc = builder.parse(webServersFile)
                    
                    val webServers = doc.getElementsByTagName("webServer")
                    for (i in 0 until webServers.length) {
                        val webServer = webServers.item(i) as Element
                        existingNames.add(webServer.getAttribute("name"))
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
        }
        
        var uniqueName = baseName
        var counter = 2
        while (existingNames.contains(uniqueName)) {
            uniqueName = "${baseName}_${counter}"
            counter++
        }
        
        cachedBaseName = baseName
        cachedProjectName = uniqueName
        return uniqueName
    }
    
    fun createSshConfigForProjects(hostName: String, hostname: String, user: String, port: Int, identityFile: String, selectedProject: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            cachedProjectName = null
            cachedBaseName = null
            val projectsDir = File(System.getProperty("user.home"), "Projects")
            if (!projectsDir.exists()) return@executeOnPooledThread
            
            projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
                val ideaDir = File(projectDir, ".idea")
                if (!ideaDir.exists()) ideaDir.mkdirs()
                
                val sshFile = File(ideaDir, "sshConfigs.xml")
                val webServersFile = File(ideaDir, "webServers.xml")
                val deploymentFile = File(ideaDir, "deployment.xml")
                val uniqueProjectName = if (sshFile.exists() || webServersFile.exists() || deploymentFile.exists()) getUniqueConfigName(selectedProject, projectDir) else selectedProject
                addOrUpdateSshConfig(sshFile, hostName, hostname, user, port, identityFile, uniqueProjectName)
            }
        }
    }
    
    fun createDeploymentConfigForProjects(hostName: String, selectedProject: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            cachedProjectName = null
            cachedBaseName = null
            println("Creating deployment config for host: $hostName, project: $selectedProject")
            val projectsDir = File(System.getProperty("user.home"), "Projects")
            if (!projectsDir.exists()) {
                println("Projects directory does not exist")
                return@executeOnPooledThread
            }
            
            projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
                println("Processing project directory: ${projectDir.name}")
                val ideaDir = File(projectDir, ".idea")
                if (!ideaDir.exists()) ideaDir.mkdirs()
                
                val sshFile = File(ideaDir, "sshConfigs.xml")
                val webServersFile = File(ideaDir, "webServers.xml")
                val deploymentFile = File(ideaDir, "deployment.xml")
                val uniqueProjectName = if (sshFile.exists() || webServersFile.exists() || deploymentFile.exists()) getUniqueConfigName(selectedProject, projectDir) else selectedProject
                
                addOrUpdateWebServer(webServersFile, hostName, uniqueProjectName)
                
                println("Creating deployment file: ${deploymentFile.absolutePath}")
                addOrUpdateDeploymentMapping(deploymentFile, uniqueProjectName)
            }
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
            
            val deploymentFile = File(projectDir, ".idea/deployment.xml")
            if (deploymentFile.exists()) {
                removeDeploymentMappingFromXml(deploymentFile, hostName)
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
            
            val configs = doc.getElementsByTagName("sshConfig")
            for (i in configs.length - 1 downTo 0) {
                val config = configs.item(i) as Element
                if (config.getAttribute("id") == hostName) {
                    config.parentNode.removeChild(config)
                }
            }
            
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
                if (config.getAttribute("customName") == hostName || config.getAttribute("id") == hostName) {
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
    
    private fun addOrUpdateDeploymentMapping(xmlFile: File, selectedProject: String) {
        println("Processing deployment file: ${xmlFile.absolutePath}")
        try {
            val doc = if (xmlFile.exists()) {
                println("File exists, parsing existing")
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                builder.parse(xmlFile)
            } else {
                println("File does not exist, creating new")
                createEmptyDeploymentDocument()
            }
            
            val component = doc.getElementsByTagName("component").item(0) as Element
            component.setAttribute("serverName", selectedProject)
            
            var serverDataElement = doc.getElementsByTagName("serverData").item(0) as? Element
            if (serverDataElement == null) {
                println("serverData element not found, creating")
                serverDataElement = doc.createElement("serverData")
                component.appendChild(serverDataElement)
            }
            
            val pathsElements = doc.getElementsByTagName("paths")
            for (i in pathsElements.length - 1 downTo 0) {
                val paths = pathsElements.item(i) as Element
                if (paths.getAttribute("name") == selectedProject) {
                    paths.parentNode.removeChild(paths)
                }
            }
            
            println("Creating paths element for project: $selectedProject")
            val paths = doc.createElement("paths")
            paths.setAttribute("name", selectedProject)
            
            val serverdata = doc.createElement("serverdata")
            val mappings = doc.createElement("mappings")
            val mapping = doc.createElement("mapping")
            mapping.setAttribute("deploy", "/")
            mapping.setAttribute("local", "\$PROJECT_DIR\$")
            mapping.setAttribute("web", "/home/dev/backend")
            
            mappings.appendChild(mapping)
            serverdata.appendChild(mappings)
            paths.appendChild(serverdata)
            serverDataElement.appendChild(paths)
            
            println("Saving document to: ${xmlFile.absolutePath}")
            saveDocument(doc, xmlFile)
            println("Document saved successfully")
        } catch (e: Exception) {
            println("Error creating deployment.xml: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun removeDeploymentMappingFromXml(xmlFile: File, hostName: String) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xmlFile)
            
            val pathsElements = doc.getElementsByTagName("paths")
            for (i in pathsElements.length - 1 downTo 0) {
                val paths = pathsElements.item(i) as Element
                if (paths.getAttribute("name") == hostName) {
                    paths.parentNode.removeChild(paths)
                }
            }
            
            saveDocument(doc, xmlFile)
        } catch (e: Exception) {
            println("Error removing from deployment.xml: ${e.message}")
        }
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
    
    private fun createEmptyDeploymentDocument(): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()
        
        val project = doc.createElement("project")
        project.setAttribute("version", "4")
        doc.appendChild(project)
        
        val component = doc.createElement("component")
        component.setAttribute("name", "PublishConfigData")
        component.setAttribute("remoteFilesAllowedToDisappearOnAutoupload", "false")
        component.setAttribute("confirmBeforeUploading", "false")
        component.setAttribute("serverName", "")
        project.appendChild(component)
        
        val option = doc.createElement("option")
        option.setAttribute("name", "confirmBeforeUploading")
        option.setAttribute("value", "false")
        component.appendChild(option)
        
        val serverData = doc.createElement("serverData")
        component.appendChild(serverData)
        
        return doc
    }
    
    private fun saveDocument(doc: Document, file: File) {
        val transformerFactory = TransformerFactory.newInstance()
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "false")
        
        val source = DOMSource(doc)
        val result = StreamResult(file)
        transformer.transform(source, result)
    }
    

}