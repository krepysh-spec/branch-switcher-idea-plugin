package com.github.krepysh.branchswitcher.service

import com.intellij.openapi.application.ApplicationManager
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

class DataSourceService {
    
    private val projectsDir = File(System.getProperty("user.home"), "Projects")
    
    fun createDataSourceForProjects(hostName: String, hostname: String, port: Int, dbName: String, username: String, projectName: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val configName = generateUniqueDataSourceName(projectName)
            val uuid = UUID.randomUUID().toString()
            processAllProjects { projectDir ->
                val dataSourceFile = File(projectDir, ".idea/dataSources.xml")
                val localDataSourceFile = File(projectDir, ".idea/dataSources.local.xml")
                addDataSource(dataSourceFile, hostName, hostname, port, dbName, username, configName, uuid)
                addLocalDataSource(localDataSourceFile, hostName, username, configName, uuid)
            }
        }
    }
    
    fun removeDataSourceFromProjects(hostName: String) {
        processAllProjects { projectDir ->
            val dataSourceFile = File(projectDir, ".idea/dataSources.xml")
            val localDataSourceFile = File(projectDir, ".idea/dataSources.local.xml")
            if (dataSourceFile.exists()) {
                removeDataSource(dataSourceFile, hostName)
            }
            if (localDataSourceFile.exists()) {
                removeLocalDataSource(localDataSourceFile, hostName)
            }
        }
    }
    
    fun removeDataSourceFromProjectsByProjectName(projectName: String) {
        processAllProjects { projectDir ->
            val dataSourceFile = File(projectDir, ".idea/dataSources.xml")
            val localDataSourceFile = File(projectDir, ".idea/dataSources.local.xml")
            if (dataSourceFile.exists()) {
                removeDataSourceByProjectName(dataSourceFile, projectName)
            }
            if (localDataSourceFile.exists()) {
                removeLocalDataSourceByProjectName(localDataSourceFile, projectName)
            }
        }
    }
    
    private fun generateUniqueDataSourceName(projectName: String): String {
        val existingNames = getAllExistingDataSourceNames()
        
        var uniqueName = projectName
        var counter = 2
        while (existingNames.contains(uniqueName)) {
            uniqueName = "${projectName}_${counter}"
            counter++
        }
        
        return uniqueName
    }
    
    private fun getAllExistingDataSourceNames(): Set<String> {
        val existingNames = mutableSetOf<String>()
        
        processAllProjects { projectDir ->
            val dataSourceFile = File(projectDir, ".idea/dataSources.xml")
            if (dataSourceFile.exists()) {
                existingNames.addAll(extractDataSourceNames(dataSourceFile))
            }
        }
        
        return existingNames
    }
    
    private fun extractDataSourceNames(dataSourceFile: File): Set<String> {
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(dataSourceFile)
            val dataSources = doc.getElementsByTagName("data-source")
            if (dataSources.length == 0) return emptySet()
            
            dataSources.asSequence()
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
                val ideaDir = File(projectDir, ".idea")
                if (!ideaDir.exists()) ideaDir.mkdirs()
                action(projectDir)
            }
    }
    
    private fun addDataSource(xmlFile: File, hostName: String, hostname: String, port: Int, dbName: String, username: String, configName: String, uuid: String) {
        val doc = if (xmlFile.exists()) parseXmlDocument(xmlFile) else createEmptyDataSourceDocument()
        
        removeExistingDataSource(doc, hostName, configName)
        
        val component = doc.getElementsByTagName("component").item(0) as Element
        val dataSource = doc.createElement("data-source").apply {
            setAttribute("source", "LOCAL")
            setAttribute("name", configName)
            setAttribute("uuid", uuid)
            
            appendChild(doc.createElement("driver-ref").apply {
                textContent = "postgresql"
            })
            appendChild(doc.createElement("synchronize").apply {
                textContent = "true"
            })
            appendChild(doc.createElement("jdbc-driver").apply {
                textContent = "org.postgresql.Driver"
            })
            appendChild(doc.createElement("jdbc-url").apply {
                textContent = "jdbc:postgresql://$hostname:$port/$dbName"
            })
            appendChild(doc.createElement("working-dir").apply {
                textContent = "\$ProjectFileDir\$"
            })
        }
        
        component.appendChild(dataSource)
        saveDocument(doc, xmlFile)
    }
    
    private fun addLocalDataSource(localXmlFile: File, hostName: String, username: String, configName: String, uuid: String) {
        val doc = if (localXmlFile.exists()) parseXmlDocument(localXmlFile) else createEmptyLocalDataSourceDocument()
        
        removeExistingLocalDataSource(doc, hostName, configName)
        
        val component = doc.getElementsByTagName("component").item(0) as Element
        val dataSource = doc.createElement("data-source").apply {
            setAttribute("name", configName)
            setAttribute("uuid", uuid)
            
            appendChild(doc.createElement("database-info").apply {
                setAttribute("product", "")
                setAttribute("version", "")
                setAttribute("jdbc-version", "")
                setAttribute("driver-name", "")
                setAttribute("driver-version", "")
                setAttribute("dbms", "POSTGRES")
            })
            appendChild(doc.createElement("user-name").apply {
                textContent = username
            })
            appendChild(doc.createElement("schema-mapping").apply {
                appendChild(doc.createElement("introspection-scope").apply {
                    appendChild(doc.createElement("node").apply {
                        setAttribute("kind", "database")
                        setAttribute("qname", "@")
                        appendChild(doc.createElement("node").apply {
                            setAttribute("kind", "schema")
                            setAttribute("negative", "1")
                        })
                    })
                })
            })
            appendChild(doc.createElement("ssh-properties").apply {
                appendChild(doc.createElement("enabled").apply { textContent = "true" })
                appendChild(doc.createElement("ssh-config-id").apply { textContent = hostName })
            })
        }
        
        component.appendChild(dataSource)
        saveDocument(doc, localXmlFile)
    }
    
    private fun removeDataSource(xmlFile: File, hostName: String) {
        try {
            val doc = parseXmlDocument(xmlFile)
            removeExistingDataSource(doc, hostName, hostName)
            
            // Check if there are any data sources left
            val remainingDataSources = doc.getElementsByTagName("data-source")
            if (remainingDataSources.length == 0) {
                // If no data sources left, delete the file
                xmlFile.delete()
            } else {
                // Save the cleaned document
                saveDocument(doc, xmlFile)
            }
        } catch (e: Exception) {
            // If parsing fails, delete the corrupted file
            xmlFile.delete()
        }
    }
    
    private fun removeLocalDataSource(localXmlFile: File, hostName: String) {
        try {
            val doc = parseXmlDocument(localXmlFile)
            removeExistingLocalDataSource(doc, hostName, hostName)
            
            // Check if there are any data sources left
            val remainingDataSources = doc.getElementsByTagName("data-source")
            if (remainingDataSources.length == 0) {
                // If no data sources left, delete the file
                localXmlFile.delete()
            } else {
                // Save the cleaned document
                saveDocument(doc, localXmlFile)
            }
        } catch (e: Exception) {
            // If parsing fails, delete the corrupted file
            localXmlFile.delete()
        }
    }
    
    private fun removeDataSourceByProjectName(xmlFile: File, projectName: String) {
        try {
            val doc = parseXmlDocument(xmlFile)
            val dataSources = doc.getElementsByTagName("data-source")
            for (i in dataSources.length - 1 downTo 0) {
                val dataSource = dataSources.item(i) as Element
                if (dataSource.getAttribute("name") == projectName) {
                    dataSource.parentNode.removeChild(dataSource)
                }
            }
            
            // Check if there are any data sources left
            val remainingDataSources = doc.getElementsByTagName("data-source")
            if (remainingDataSources.length == 0) {
                // If no data sources left, delete the file
                xmlFile.delete()
            } else {
                // Save the cleaned document
                saveDocument(doc, xmlFile)
            }
        } catch (e: Exception) {
            // If parsing fails, delete the corrupted file
            xmlFile.delete()
        }
    }
    
    private fun removeLocalDataSourceByProjectName(localXmlFile: File, projectName: String) {
        try {
            val doc = parseXmlDocument(localXmlFile)
            val dataSources = doc.getElementsByTagName("data-source")
            for (i in dataSources.length - 1 downTo 0) {
                val dataSource = dataSources.item(i) as Element
                if (dataSource.getAttribute("name") == projectName) {
                    dataSource.parentNode.removeChild(dataSource)
                }
            }
            
            // Check if there are any data sources left
            val remainingDataSources = doc.getElementsByTagName("data-source")
            if (remainingDataSources.length == 0) {
                // If no data sources left, delete the file
                localXmlFile.delete()
            } else {
                // Save the cleaned document
                saveDocument(doc, localXmlFile)
            }
        } catch (e: Exception) {
            // If parsing fails, delete the corrupted file
            localXmlFile.delete()
        }
    }
    
    private fun removeExistingDataSource(doc: Document, hostName: String, configName: String) {
        val dataSources = doc.getElementsByTagName("data-source")
        for (i in dataSources.length - 1 downTo 0) {
            val dataSource = dataSources.item(i) as Element
            if (dataSource.getAttribute("name") == hostName || dataSource.getAttribute("name") == configName) {
                dataSource.parentNode.removeChild(dataSource)
            }
        }
    }
    
    private fun removeExistingLocalDataSource(doc: Document, hostName: String, configName: String) {
        val dataSources = doc.getElementsByTagName("data-source")
        for (i in dataSources.length - 1 downTo 0) {
            val dataSource = dataSources.item(i) as Element
            if (dataSource.getAttribute("name") == hostName || dataSource.getAttribute("name") == configName) {
                dataSource.parentNode.removeChild(dataSource)
            }
        }
    }
    
    private fun parseXmlDocument(file: File): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    
    private fun createEmptyDataSourceDocument(): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().apply {
            appendChild(createElement("project").apply {
                setAttribute("version", "4")
                appendChild(createElement("component").apply {
                    setAttribute("name", "DataSourceManagerImpl")
                    setAttribute("format", "xml")
                    setAttribute("multifile-model", "true")
                })
            })
        }
    
    private fun createEmptyLocalDataSourceDocument(): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().apply {
            appendChild(createElement("project").apply {
                setAttribute("version", "4")
                appendChild(createElement("component").apply {
                    setAttribute("name", "dataSourceStorageLocal")
                })
            })
        }
    
    private fun shouldCreateFile(doc: Document): Boolean {
        val dataSources = doc.getElementsByTagName("data-source")
        return dataSources.length > 0
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