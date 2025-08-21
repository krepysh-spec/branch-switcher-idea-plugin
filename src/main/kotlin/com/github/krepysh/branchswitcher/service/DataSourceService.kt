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
    
    fun createDataSourceForProjects(hostName: String, hostname: String, port: Int, dbName: String, username: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            processAllProjects { projectDir ->
                val dataSourceFile = File(projectDir, ".idea/dataSources.xml")
                addDataSource(dataSourceFile, hostName, hostname, port, dbName, username)
            }
        }
    }
    
    fun removeDataSourceFromProjects(hostName: String) {
        processAllProjects { projectDir ->
            val dataSourceFile = File(projectDir, ".idea/dataSources.xml")
            if (dataSourceFile.exists()) {
                removeDataSource(dataSourceFile, hostName)
            }
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
    
    private fun addDataSource(xmlFile: File, hostName: String, hostname: String, port: Int, dbName: String, username: String) {
        val doc = if (xmlFile.exists()) parseXmlDocument(xmlFile) else createEmptyDataSourceDocument()
        
        removeExistingDataSource(doc, hostName)
        
        val component = doc.getElementsByTagName("component").item(0) as Element
        val dataSource = doc.createElement("data-source").apply {
            setAttribute("source", "LOCAL")
            setAttribute("name", hostName)
            setAttribute("uuid", UUID.randomUUID().toString())
            
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
    
    private fun removeDataSource(xmlFile: File, hostName: String) {
        try {
            val doc = parseXmlDocument(xmlFile)
            removeExistingDataSource(doc, hostName)
            saveDocument(doc, xmlFile)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun removeExistingDataSource(doc: Document, hostName: String) {
        val dataSources = doc.getElementsByTagName("data-source")
        for (i in dataSources.length - 1 downTo 0) {
            val dataSource = dataSources.item(i) as Element
            if (dataSource.getAttribute("name") == hostName) {
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
    
    private fun saveDocument(doc: Document, file: File) {
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8")
        }.transform(DOMSource(doc), StreamResult(file))
    }
}