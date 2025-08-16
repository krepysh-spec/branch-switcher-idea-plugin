package com.github.krepysh.branchswitcher.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.github.krepysh.branchswitcher.parser.SshConfigParser
import com.github.krepysh.branchswitcher.model.SshHost
import java.awt.*
import javax.swing.*

sealed class ListItem
data class ProjectHeader(val name: String, var isExpanded: Boolean = true) : ListItem()
data class HostItem(val host: SshHost, val project: String?) : ListItem()

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow()
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        
        val actionGroup = DefaultActionGroup()
        actionGroup.add(myToolWindow.RefreshAction())
        actionGroup.add(myToolWindow.CreateConfigAction())
        actionGroup.add(myToolWindow.AddHostAction())
        actionGroup.addSeparator()
        actionGroup.add(myToolWindow.OpenSshSettingsAction())
        actionGroup.add(myToolWindow.OpenDeploymentSettingsAction())
        
        val toolbar = ActionManager.getInstance().createActionToolbar("BranchSwitcher", actionGroup, true)
        content.setActions(actionGroup, ActionPlaces.TOOLBAR, toolbar.component)
        
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow {
        private val parser = SshConfigParser()
        private val listModel = DefaultListModel<ListItem>()
        private val hostList = JBList(listModel)
        private val expandedProjects = mutableSetOf<String>()
        
        init {
            hostList.cellRenderer = SshHostRenderer()
            hostList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            refreshHosts()
        }

        fun getContent(): JComponent {
            val mainPanel = JPanel(BorderLayout())
            
            val toolbarGroup = DefaultActionGroup()
            toolbarGroup.add(AddHostToolbarAction())
            toolbarGroup.add(EditHostToolbarAction())
            toolbarGroup.add(DuplicateHostToolbarAction())
            toolbarGroup.add(DeleteHostToolbarAction())
            toolbarGroup.add(RefreshToolbarAction())
            toolbarGroup.add(OpenSshToolbarAction())
            toolbarGroup.add(OpenDeploymentToolbarAction())
            val toolbar = ActionManager.getInstance().createActionToolbar("BranchSwitcherList", toolbarGroup, true)
            toolbar.targetComponent = hostList
            
            val popup = JPopupMenu()
            val editItem = JMenuItem("Edit", AllIcons.Actions.Edit)
            val deleteItem = JMenuItem("Delete", AllIcons.Actions.Cancel)
            
            editItem.addActionListener { 
                val selected = hostList.selectedValue
                if (selected is HostItem) {
                    println("Showing edit dialog for: ${selected.host.name}")
                    showEditHostDialog(selected.host)
                }
            }
            deleteItem.addActionListener { 
                val selected = hostList.selectedValue
                if (selected is HostItem) {
                    println("Deleting host: ${selected.host.name}")
                    deleteHost(selected.host)
                }
            }
            
            popup.add(editItem)
            popup.add(deleteItem)
            
            // Add mouse listener for right-click and project header clicks
            hostList.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) {
                    if (e.isPopupTrigger) {
                        val index = hostList.locationToIndex(e.point)
                        if (index >= 0) {
                            hostList.selectedIndex = index
                            popup.show(hostList, e.x, e.y)
                        }
                    }
                }
                
                override fun mouseReleased(e: java.awt.event.MouseEvent) {
                    if (e.isPopupTrigger) {
                        val index = hostList.locationToIndex(e.point)
                        if (index >= 0) {
                            hostList.selectedIndex = index
                            popup.show(hostList, e.x, e.y)
                        }
                    }
                }
                
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 1) {
                        val index = hostList.locationToIndex(e.point)
                        if (index >= 0) {
                            val item = listModel.getElementAt(index)
                            if (item is ProjectHeader) {
                                toggleProjectExpansion(item)
                            }
                        }
                    }
                }
            })
            
            val scrollPane = JBScrollPane(hostList)
            
            mainPanel.add(toolbar.component, BorderLayout.NORTH)
            mainPanel.add(scrollPane, BorderLayout.CENTER)
            
            return mainPanel
        }
        
        private fun refreshHosts() {
            listModel.clear()
            val configFile = java.io.File(System.getProperty("user.home"), ".ssh/config")
            
            if (configFile.exists()) {
                val hosts = parser.parseConfig()
                val hostsWithProjects = hosts.map { host ->
                    val project = getProjectForHost(host.name)
                    HostItem(host, project)
                }
                
                val grouped = hostsWithProjects.groupBy { it.project ?: "No Project" }
                
                // Initialize all projects as expanded if expandedProjects is empty
                if (expandedProjects.isEmpty()) {
                    expandedProjects.addAll(grouped.keys)
                }
                
                grouped.forEach { (project, hostItems) ->
                    val isExpanded = expandedProjects.contains(project)
                    val header = ProjectHeader(project, isExpanded)
                    listModel.addElement(header)
                    
                    if (isExpanded) {
                        hostItems.forEach { listModel.addElement(it) }
                    }
                }
            }
        }
        
        private fun toggleProjectExpansion(header: ProjectHeader) {
            if (header.isExpanded) {
                expandedProjects.remove(header.name)
            } else {
                expandedProjects.add(header.name)
            }
            refreshHosts()
        }
        
        inner class RefreshAction : AnAction("Refresh", "Refresh SSH hosts", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                refreshHosts()
            }
        }
        
        inner class CreateConfigAction : AnAction("Create Config", "Create SSH config file", AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) {
                createSshConfig()
            }
            
            override fun update(e: AnActionEvent) {
                val configFile = java.io.File(System.getProperty("user.home"), ".ssh/config")
                e.presentation.isVisible = !configFile.exists()
            }
        }
        
        inner class AddHostAction : AnAction("Add Host", "Add new SSH host", AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) {
                showAddHostDialog()
            }
            
            override fun update(e: AnActionEvent) {
                val configFile = java.io.File(System.getProperty("user.home"), ".ssh/config")
                e.presentation.isVisible = configFile.exists()
            }
        }
        
        inner class AddHostToolbarAction : AnAction("Add Host", "Add new SSH host", AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) {
                val configFile = java.io.File(System.getProperty("user.home"), ".ssh/config")
                if (!configFile.exists()) {
                    createSshConfig()
                }
                if (checkAndInitializeGateway()) {
                    showAddHostDialog()
                }
            }
        }
        
        inner class EditHostToolbarAction : AnAction("Edit", "Edit selected SSH host", AllIcons.Actions.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                val selected = hostList.selectedValue
                if (selected is HostItem) {
                    showEditHostDialog(selected.host)
                }
            }
            
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = hostList.selectedValue is HostItem
            }
        }
        
        inner class DuplicateHostToolbarAction : AnAction("Duplicate", "Duplicate selected SSH host", AllIcons.Actions.Copy) {
            override fun actionPerformed(e: AnActionEvent) {
                val selected = hostList.selectedValue
                if (selected is HostItem) {
                    duplicateHost(selected.host)
                }
            }
            
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = hostList.selectedValue is HostItem
            }
        }
        
        inner class DeleteHostToolbarAction : AnAction("Delete", "Delete selected SSH host", AllIcons.General.Remove) {
            override fun actionPerformed(e: AnActionEvent) {
                val selected = hostList.selectedValue
                if (selected is HostItem) {
                    deleteHost(selected.host)
                }
            }
            
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = hostList.selectedValue is HostItem
            }
        }
        
        inner class RefreshToolbarAction : AnAction("Refresh", "Refresh SSH hosts", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                refreshHosts()
            }
        }
        
        inner class OpenSshToolbarAction : AnAction("SSH", "Open SSH Configurations", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
                    project, "SSH Configurations"
                )
            }
        }
        
        inner class OpenDeploymentToolbarAction : AnAction("Deployment", "Open Deployment Settings", AllIcons.Nodes.Deploy) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
                    project, "Open Deployment"
                )
            }
        }
        
        inner class OpenSshSettingsAction : AnAction("SSH Settings", "Open SSH Configurations", AllIcons.General.Settings) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
                    project, "SSH Configurations"
                )
            }
        }
        
        inner class OpenDeploymentSettingsAction : AnAction("Deployment Settings", "Open Deployment Settings", AllIcons.Nodes.Deploy) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
                    project, "Deployment"
                )
            }
        }
        
        private fun checkAndInitializeGateway(): Boolean {
            if (!parser.hasGatewayHost()) {
                return showGatewayInitializationDialog()
            }
            return true
        }
        
        private fun showGatewayInitializationDialog(): Boolean {
            val result = JOptionPane.showConfirmDialog(
                null,
                "ProxyJump host 'gatesftp2' not found.\nIt needs to be initialized before starting work.\nInitialize now?",
                "Initialize ProxyJump",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )
            
            return if (result == JOptionPane.YES_OPTION) {
                showGatewaySetupDialog()
            } else {
                false
            }
        }
        
        private fun showGatewaySetupDialog(): Boolean {
            var result = false
            val dialog = JDialog()
            dialog.title = "Setup ProxyJump Host"
            dialog.isModal = true
            dialog.layout = GridBagLayout()
            
            val gbc = GridBagConstraints()
            gbc.insets = Insets(5, 5, 5, 5)
            gbc.anchor = GridBagConstraints.WEST
            
            val defaultIdentityPath = java.io.File(System.getProperty("user.home"), ".ssh/id_rsa")
            val identityField = JTextField(
                if (defaultIdentityPath.exists()) defaultIdentityPath.absolutePath else "~/.ssh/id_rsa", 
                30
            )
            val browseButton = JButton("Browse")
            browseButton.addActionListener {
                val fileChooser = JFileChooser(java.io.File(System.getProperty("user.home"), ".ssh"))
                fileChooser.dialogTitle = "Select Identity File"
                if (fileChooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                    identityField.text = fileChooser.selectedFile.absolutePath
                }
            }
            
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3
            dialog.add(JLabel("Setup ProxyJump host 'gatesftp2':"), gbc)
            
            gbc.gridy = 1; gbc.gridwidth = 1
            dialog.add(JLabel("HostName:"), gbc)
            gbc.gridx = 1; gbc.gridwidth = 2
            dialog.add(JLabel("23.109.14.108"), gbc)
            
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1
            dialog.add(JLabel("User:"), gbc)
            gbc.gridx = 1; gbc.gridwidth = 2
            dialog.add(JLabel("gateway"), gbc)
            
            gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1
            dialog.add(JLabel("IdentityFile:"), gbc)
            gbc.gridx = 1
            dialog.add(identityField, gbc)
            gbc.gridx = 2
            dialog.add(browseButton, gbc)
            
            val buttonPanel = JPanel()
            val createButton = JButton("Create")
            val cancelButton = JButton("Cancel")
            
            createButton.addActionListener {
                try {
                    val identityFile = identityField.text.trim()
                    if (identityFile.isEmpty()) {
                        JOptionPane.showMessageDialog(dialog, "Identity file is required", "Error", JOptionPane.ERROR_MESSAGE)
                        return@addActionListener
                    }
                    
                    parser.addGatewayHost(identityFile)
                    result = true
                    dialog.dispose()
                    JOptionPane.showMessageDialog(null, "ProxyJump host 'gatesftp2' created successfully!", "Success", JOptionPane.INFORMATION_MESSAGE)
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(dialog, "Error creating ProxyJump host: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
            
            cancelButton.addActionListener { dialog.dispose() }
            
            buttonPanel.add(createButton)
            buttonPanel.add(cancelButton)
            
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3
            dialog.add(buttonPanel, gbc)
            
            dialog.pack()
            dialog.setLocationRelativeTo(null)
            dialog.isVisible = true
            
            return result
        }
        
        private fun createSshConfig() {
            try {
                val sshDir = java.io.File(System.getProperty("user.home"), ".ssh")
                if (!sshDir.exists()) {
                    sshDir.mkdirs()
                }
                
                val configFile = java.io.File(sshDir, "config")
                configFile.writeText("# SSH Config\n# Add your hosts here\n")
                
                refreshHosts()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(null, "Error creating SSH config: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
        
        private fun deleteHost(host: SshHost) {
            val result = JOptionPane.showConfirmDialog(
                null,
                "Are you sure you want to delete host '${host.name}'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )
            
            if (result == JOptionPane.YES_OPTION) {
                try {
                    removeHostFromConfig(host.name)
                    refreshHosts()
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(null, "Error deleting host: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
        
        private fun removeHostFromConfig(hostName: String) {
            val configFile = java.io.File(System.getProperty("user.home"), ".ssh/config")
            if (!configFile.exists()) return
            
            val lines = configFile.readLines().toMutableList()
            println("Looking for host: $hostName")
            val startIndex = lines.indexOfFirst { line ->
                val trimmed = line.trim()
                val matches = trimmed == "Host $hostName" || trimmed.startsWith("Host $hostName ")
                if (matches) println("Found host at line: $line")
                matches
            }
            
            println("Start index: $startIndex")
            if (startIndex >= 0) {
                var endIndex = startIndex + 1
                while (endIndex < lines.size) {
                    val line = lines[endIndex]
                    if (line.trim().startsWith("Host ") && !line.startsWith("    ")) {
                        break
                    }
                    endIndex++
                }
                
                println("Removing lines from $startIndex to $endIndex")
                // Remove the host block
                for (i in endIndex - 1 downTo startIndex) {
                    println("Removing line: ${lines[i]}")
                    lines.removeAt(i)
                }
                
                // Clean up empty lines
                while (startIndex > 0 && startIndex < lines.size && lines[startIndex].trim().isEmpty()) {
                    lines.removeAt(startIndex)
                }
                
                configFile.writeText(lines.joinToString("\n"))
                println("Host $hostName removed successfully")
            } else {
                println("Host $hostName not found in config")
            }
        }
        
        private fun showAddHostDialog() {
            showHostDialog(null)
        }
        
        private fun showEditHostDialog(host: SshHost) {
            println("showEditHostDialog called for host: ${host.name}")
            showHostDialog(host)
        }
        
        private fun showHostDialog(existingHost: SshHost?) {
            val dialog = JDialog()
            dialog.title = if (existingHost == null) "Add SSH Host" else "Edit SSH Host"
            dialog.isModal = true
            dialog.layout = GridBagLayout()
            
            val gbc = GridBagConstraints()
            gbc.insets = Insets(5, 5, 5, 5)
            gbc.anchor = GridBagConstraints.WEST
            
            val projects = arrayOf("Funrize", "Scarletsands", "JackpotRabbit", "Fortunewheelz", "Funzcity", "Taofortune", "Sweepshark", "Stormrush", "Nolimitcoins")
            val projectCombo = JComboBox(projects)
            
            // Set current project if editing
            if (existingHost != null) {
                val currentProject = getProjectForHost(existingHost.name)
                if (currentProject != null) {
                    projectCombo.selectedItem = currentProject
                }
            }
            
            val hostField = JTextField(existingHost?.name ?: "", 20)
            val userField = JTextField("dev", 20)
            userField.isEnabled = false
            val portField = JTextField(existingHost?.port?.toString() ?: "22", 20)
            portField.isEnabled = false
            
            val defaultIdentityFile = parser.getDefaultIdentityFile()
            val identityField = JTextField(
                existingHost?.identityFile ?: defaultIdentityFile, 
                20
            )
            val browseButton = JButton("Browse")
            browseButton.addActionListener {
                val fileChooser = JFileChooser(java.io.File(System.getProperty("user.home"), ".ssh"))
                fileChooser.dialogTitle = "Select Identity File"
                if (fileChooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                    identityField.text = fileChooser.selectedFile.absolutePath
                }
            }
            
            val fields = listOf(
                "Project:" to projectCombo,
                "Host:" to hostField,
                "User:" to userField,
                "Port:" to portField
            )
            
            fields.forEachIndexed { index, (label, field) ->
                gbc.gridx = 0; gbc.gridy = index
                dialog.add(JLabel(label), gbc)
                gbc.gridx = 1; gbc.gridwidth = 2
                dialog.add(field, gbc)
                gbc.gridwidth = 1
            }
            
            gbc.gridx = 0; gbc.gridy = fields.size
            dialog.add(JLabel("Identity File:"), gbc)
            gbc.gridx = 1
            dialog.add(identityField, gbc)
            gbc.gridx = 2
            dialog.add(browseButton, gbc)
            
            val buttonPanel = JPanel()
            val saveButton = JButton("Save")
            val cancelButton = JButton("Cancel")
            
            saveButton.addActionListener {
                try {
                    val selectedProject = projectCombo.selectedItem as String
                    val hostValue = hostField.text.trim()
                    
                    if (hostValue.isEmpty()) {
                        JOptionPane.showMessageDialog(dialog, "Host is required", "Error", JOptionPane.ERROR_MESSAGE)
                        return@addActionListener
                    }
                    
                    saveHost(existingHost?.name, hostValue, hostValue, 
                           userField.text.trim(), portField.text.trim(), identityField.text.trim(), selectedProject)
                    dialog.dispose()
                    refreshHosts()
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(dialog, "Error saving host: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
            
            cancelButton.addActionListener { dialog.dispose() }
            
            buttonPanel.add(saveButton)
            buttonPanel.add(cancelButton)
            
            gbc.gridx = 0; gbc.gridy = fields.size + 1
            gbc.gridwidth = 3
            dialog.add(buttonPanel, gbc)
            
            dialog.pack()
            dialog.setLocationRelativeTo(null)
            dialog.isVisible = true
        }
        
        private fun getProjectForHost(hostName: String): String? {
            val configFile = java.io.File(System.getProperty("user.home"), ".ssh/config")
            if (!configFile.exists()) return null
            
            val lines = configFile.readLines()
            val hostIndex = lines.indexOfFirst { it.trim() == "Host $hostName" }
            if (hostIndex > 0) {
                val prevLine = lines[hostIndex - 1].trim()
                if (prevLine.startsWith("# Project:")) {
                    return prevLine.substringAfter("# Project:").trim()
                }
            }
            return null
        }
        
        private fun saveHost(oldName: String?, name: String, hostname: String, user: String, port: String, identityFile: String, project: String) {
            val configFile = java.io.File(System.getProperty("user.home"), ".ssh/config")
            val lines = if (configFile.exists()) configFile.readLines().toMutableList() else mutableListOf()
            
            if (oldName != null) {
                val hostIndex = lines.indexOfFirst { it.trim() == "Host $oldName" }
                if (hostIndex >= 0) {
                    var startIndex = hostIndex
                    // Check if there's a project comment before the host
                    if (hostIndex > 0 && lines[hostIndex - 1].trim().startsWith("# Project:")) {
                        startIndex = hostIndex - 1
                    }
                    
                    var endIndex = hostIndex + 1
                    while (endIndex < lines.size && (lines[endIndex].startsWith("    ") || lines[endIndex].trim().isEmpty())) {
                        endIndex++
                    }
                    
                    // Remove from startIndex to endIndex
                    for (i in endIndex - 1 downTo startIndex) {
                        lines.removeAt(i)
                    }
                }
            }
            
            lines.add("")
            lines.add("# Project: $project")
            lines.add("Host $name")
            if (user.isNotEmpty()) lines.add("    User $user")
            if (port.isNotEmpty()) lines.add("    Port $port")
            if (identityFile.isNotEmpty()) lines.add("    IdentityFile $identityFile")
            if (hostname.isNotEmpty()) lines.add("    HostName $hostname")
            lines.add("    ProxyJump gatesftp2")
            
            configFile.writeText(lines.joinToString("\n"))
        }
        
        private fun duplicateHost(host: SshHost) {
            val newName = "${host.name}_copy"
            val originalProject = getProjectForHost(host.name) ?: "Unknown"
            saveHost(null, newName, host.hostname ?: "", host.user ?: "", host.port?.toString() ?: "", host.identityFile ?: "", originalProject)
            refreshHosts()
        }
    }
    
    class SshHostRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            when (value) {
                is ProjectHeader -> {
                    icon = if (value.isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
                    text = value.name
                    font = font.deriveFont(Font.BOLD)
                    border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
                }
                is HostItem -> {
                    icon = AllIcons.Nodes.DataTables
                    text = buildString {
                        append(value.host.name)
                        value.host.hostname?.let { append(" ($it)") }
                        value.host.user?.let { append(" - $it") }
                    }
                    font = font.deriveFont(Font.PLAIN)
                    border = BorderFactory.createEmptyBorder(2, 20, 2, 2)
                }
            }
            
            return this
        }
    }
}