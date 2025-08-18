package com.github.krepysh.branchswitcher.toolWindow

import com.intellij.icons.AllIcons

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.github.krepysh.branchswitcher.parser.SshConfigParser
import com.github.krepysh.branchswitcher.model.SshHost
import com.github.krepysh.branchswitcher.service.SshConfigService
import java.awt.*
import java.io.File
import javax.swing.*

enum class ConnectionStatus { UNKNOWN, TESTING, SUCCESS, FAILED }

sealed class ListItem
data class ProjectHeader(val name: String, var isExpanded: Boolean = true) : ListItem()
data class HostItem(val host: SshHost, val project: String?, var connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN) : ListItem()

class SshHostsToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val sshHostsToolWindow = SshHostsToolWindow()
        val content = ContentFactory.getInstance().createContent(sshHostsToolWindow.getContent(), null, false)
        
        val actionGroup = DefaultActionGroup()
        actionGroup.add(sshHostsToolWindow.RefreshAction())
        actionGroup.add(sshHostsToolWindow.CreateConfigAction())
        actionGroup.add(sshHostsToolWindow.AddHostAction())
        actionGroup.addSeparator()
        actionGroup.add(sshHostsToolWindow.OpenSshSettingsAction())
        actionGroup.add(sshHostsToolWindow.OpenDeploymentSettingsAction())
        
        val toolbar = ActionManager.getInstance().createActionToolbar("BranchSwitcher", actionGroup, true)
        content.setActions(actionGroup, ActionPlaces.TOOLBAR, toolbar.component)
        
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class SshHostsToolWindow {
        private val parser = SshConfigParser()
        private val sshService = SshConfigService()
        private val listModel = DefaultListModel<ListItem>()
        private val hostList = JBList(listModel)
        private val expandedProjects = mutableSetOf<String>()
        
        init {
            hostList.cellRenderer = SshHostListRenderer()
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
            toolbarGroup.addSeparator()
            toolbarGroup.add(TestConnectionToolbarAction())
            toolbarGroup.add(RefreshToolbarAction())
            toolbarGroup.addSeparator()
            toolbarGroup.add(OpenSshToolbarAction())
            toolbarGroup.add(OpenDeploymentToolbarAction())
            val toolbar = ActionManager.getInstance().createActionToolbar("BranchSwitcherList", toolbarGroup, true)
            toolbar.targetComponent = hostList
            
            val popup = JPopupMenu()
            val editItem = JMenuItem("Edit", AllIcons.Actions.Edit)
            val deleteItem = JMenuItem("Delete", AllIcons.Actions.Cancel)
            val testItem = JMenuItem("Test Connection", AllIcons.Actions.Execute)
            
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
            testItem.addActionListener {
                val selected = hostList.selectedValue
                if (selected is HostItem) {
                    testConnectionWithStatus(selected)
                }
            }
            
            popup.add(editItem)
            popup.add(deleteItem)
            popup.add(testItem)
            
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
        
        inner class TestConnectionToolbarAction : AnAction("Test Connection", "Test SSH connection", AllIcons.Actions.Execute) {
            override fun actionPerformed(e: AnActionEvent) {
                val selected = hostList.selectedValue
                if (selected is HostItem) {
                    testConnectionWithStatus(selected)
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
                    project, "Deployment"
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
            if (!sshService.hasGatewayHost()) {
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
                    
                    sshService.addGatewayHost(identityFile)
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
            val deleteFromSshConfigsCheckbox = JCheckBox("Also delete from IntelliJ SSH configs", true)
            val message = arrayOf(
                "Are you sure you want to delete host '${host.name}'?",
                deleteFromSshConfigsCheckbox
            )
            
            val result = JOptionPane.showConfirmDialog(
                null,
                message,
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )
            
            if (result == JOptionPane.YES_OPTION) {
                try {
                    removeHostFromConfig(host.name)
                    
                    if (deleteFromSshConfigsCheckbox.isSelected) {
                        removeHostFromSshConfigs(host.name)
                    }
                    
                    refreshHosts()
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(null, "Error deleting host: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
        
        private fun removeHostFromSshConfigs(hostName: String) {
            val projectsDir = File(System.getProperty("user.home"), "Projects")
            if (!projectsDir.exists()) return
            
            projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
                val sshFile = File(projectDir, ".idea/sshConfigs.xml")
                if (sshFile.exists()) {
                    val content = sshFile.readText()
                    val regex = Regex("\\s*<sshConfig[^>]*id=\"$hostName\"[^>]*>.*?</sshConfig>", RegexOption.DOT_MATCHES_ALL)
                    val updatedContent = content.replace(regex, "")
                    sshFile.writeText(updatedContent)
                }
            }
        }
        
        private fun removeHostFromConfig(hostName: String) {
            val project = getProjectForHost(hostName)
            if (project != null) {
                sshService.removeProjectHost(project.lowercase(), hostName)
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
            
            val contentPanel = JPanel(GridBagLayout())
            contentPanel.border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
            
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
            
            val defaultIdentityFile = sshService.getDefaultIdentityFile()
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
            
            val createSshConfigCheckbox = JCheckBox("Create SSH config for IntelliJ IDEA", true)
            
            val fields = listOf(
                "Project:" to projectCombo,
                "Host:" to hostField,
                "User:" to userField,
                "Port:" to portField
            )
            
            // Add test connection button next to host field
            val testButton = JButton("Test")
            
            testButton.addActionListener {
                val hostValue = hostField.text.trim()
                if (hostValue.isNotEmpty()) {
                    testButton.isEnabled = false
                    testButton.text = "Testing..."
                    
                    Thread {
                        val success = try {
                            ProcessBuilder("ssh", "-o", "ConnectTimeout=10", "-o", "BatchMode=yes", 
                                          hostValue, "echo", "Connection successful")
                                .start().waitFor() == 0
                        } catch (e: Exception) { false }
                        
                        SwingUtilities.invokeLater {
                            testButton.isEnabled = true
                            testButton.text = "Test"
                            val message = if (success) "Connection successful!" else "Connection failed!"
                            val messageType = if (success) JOptionPane.INFORMATION_MESSAGE else JOptionPane.ERROR_MESSAGE
                            JOptionPane.showMessageDialog(contentPanel, message, "Connection Test", messageType)
                        }
                    }.start()
                } else {
                    JOptionPane.showMessageDialog(contentPanel, "Please enter a host name first", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
            
            // Update host field layout to include test button
            val hostPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
            hostPanel.add(hostField)
            hostPanel.add(Box.createHorizontalStrut(5))
            hostPanel.add(testButton)
            
            // Replace host field in the layout
            fields.forEachIndexed { index, (label, field) ->
                gbc.gridx = 0; gbc.gridy = index
                contentPanel.add(JLabel(label), gbc)
                gbc.gridx = 1; gbc.gridwidth = 2
                if (field == hostField) {
                    contentPanel.add(hostPanel, gbc)
                } else {
                    contentPanel.add(field, gbc)
                }
                gbc.gridwidth = 1
            }
            
            gbc.gridx = 0; gbc.gridy = fields.size
            contentPanel.add(JLabel("Identity File:"), gbc)
            gbc.gridx = 1
            contentPanel.add(identityField, gbc)
            gbc.gridx = 2
            contentPanel.add(browseButton, gbc)
            
            gbc.gridx = 0; gbc.gridy = fields.size + 1; gbc.gridwidth = 3
            contentPanel.add(createSshConfigCheckbox, gbc)
            gbc.gridwidth = 1
            
            val buttonPanel = JPanel()
            val saveButton = JButton("Save")
            val cancelButton = JButton("Cancel")
            
            saveButton.addActionListener {
                try {
                    val selectedProject = projectCombo.selectedItem as String
                    val hostValue = hostField.text.trim()
                    val identityFile = identityField.text.trim()
                    
                    if (hostValue.isEmpty()) {
                        JOptionPane.showMessageDialog(dialog, "Host is required", "Error", JOptionPane.ERROR_MESSAGE)
                        return@addActionListener
                    }
                    
                    // Check host uniqueness
                    if (existingHost?.name != hostValue && isHostExists(hostValue)) {
                        JOptionPane.showMessageDialog(dialog, "Host '$hostValue' already exists", "Error", JOptionPane.ERROR_MESSAGE)
                        return@addActionListener
                    }
                    
                    // Validate identity file
                    if (identityFile.isNotEmpty() && !isValidIdentityFile(identityFile)) {
                        JOptionPane.showMessageDialog(dialog, "Invalid identity file: $identityFile", "Error", JOptionPane.ERROR_MESSAGE)
                        return@addActionListener
                    }
                    
                    saveHost(existingHost?.name, hostValue, hostValue, 
                           userField.text.trim(), portField.text.trim(), identityFile, selectedProject)
                    
                    // Create SSH XML for IntelliJ projects if checkbox is selected
                    if (createSshConfigCheckbox.isSelected) {
                        createSshXmlForProjects(hostValue, hostValue, userField.text.trim(), portField.text.trim().toIntOrNull() ?: 22, identityFile, selectedProject)
                    }
                    
                    dialog.dispose()
                    refreshHosts()
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(dialog, "Error saving host: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
            
            cancelButton.addActionListener { dialog.dispose() }
            
            buttonPanel.add(saveButton)
            buttonPanel.add(cancelButton)
            
            gbc.gridx = 0; gbc.gridy = fields.size + 2
            gbc.gridwidth = 3
            contentPanel.add(buttonPanel, gbc)
            
            dialog.contentPane = contentPanel
            dialog.pack()
            dialog.setLocationRelativeTo(null)
            dialog.isVisible = true
        }
        
        private fun getProjectForHost(hostName: String): String? {
            val confDDir = com.github.krepysh.branchswitcher.util.FileUtils.getSshConfDDir()
            if (!confDDir.exists()) return null
            
            confDDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
                val hostFile = java.io.File(projectDir, hostName)
                if (hostFile.exists()) {
                    return projectDir.name.replaceFirstChar { it.uppercase() }
                }
            }
            return null
        }
        
        private fun saveHost(oldName: String?, name: String, hostname: String, user: String, port: String, identityFile: String, project: String) {
            // Remove old host if editing
            if (oldName != null && oldName != name) {
                sshService.removeProjectHost(project, oldName)
            }
            
            // Add new host using conf.d structure
            sshService.addProjectHost(
                projectName = project,
                hostName = name,
                hostname = hostname,
                user = user,
                port = port.toIntOrNull(),
                identityFile = identityFile.takeIf { it.isNotEmpty() }
            )
        }
        
        private fun testConnectionWithStatus(hostItem: HostItem) {
            hostItem.connectionStatus = ConnectionStatus.TESTING
            hostList.repaint()
            
            Thread {
                try {
                    val process = ProcessBuilder(
                        "ssh", "-o", "ConnectTimeout=10", "-o", "BatchMode=yes", 
                        hostItem.host.name, "echo", "Connection successful"
                    ).start()
                    
                    val success = process.waitFor() == 0
                    
                    SwingUtilities.invokeLater {
                        hostItem.connectionStatus = if (success) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED
                        hostList.repaint()
                        
                        val project = ProjectManager.getInstance().defaultProject
                        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SSH Connection")
                        
                        if (success) {
                            notificationGroup.createNotification(
                                "Connection successful to ${hostItem.host.name}",
                                NotificationType.INFORMATION
                            ).notify(project)
                        } else {
                            notificationGroup.createNotification(
                                "Connection failed to ${hostItem.host.name}",
                                NotificationType.ERROR
                            ).notify(project)
                        }
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        hostItem.connectionStatus = ConnectionStatus.FAILED
                        hostList.repaint()
                        
                        val project = ProjectManager.getInstance().defaultProject
                        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SSH Connection")
                        notificationGroup.createNotification(
                            "Connection error to ${hostItem.host.name}: ${e.message}",
                            NotificationType.ERROR
                        ).notify(project)
                    }
                }
            }.start()
        }
        
        private fun isHostExists(hostName: String): Boolean {
            val hosts = parser.parseConfig()
            return hosts.any { it.name == hostName }
        }
        
        private fun isValidIdentityFile(filePath: String): Boolean {
            val expandedPath = if (filePath.startsWith("~/")) {
                System.getProperty("user.home") + filePath.substring(1)
            } else {
                filePath
            }
            
            val file = java.io.File(expandedPath)
            if (!file.exists() || !file.isFile) return false
            
            // Basic validation for SSH private key format
            try {
                val content = file.readText()
                return content.contains("BEGIN") && content.contains("PRIVATE KEY") && content.contains("END")
            } catch (e: Exception) {
                return false
            }
        }
        
        private fun duplicateHost(host: SshHost) {
            val newName = "${host.name}_copy"
            val originalProject = getProjectForHost(host.name) ?: "Unknown"
            saveHost(null, newName, host.hostname ?: "", host.user ?: "", host.port?.toString() ?: "", host.identityFile ?: "", originalProject)
            
            // Create SSH XML for duplicated host
            createSshXmlForProjects(newName, host.hostname ?: "", host.user ?: "dev", host.port ?: 22, host.identityFile ?: "", originalProject)
            
            refreshHosts()
        }
        
        private fun createSshXmlForProjects(hostName: String, hostname: String, user: String, port: Int, identityFile: String, selectedProject: String) {
            val projectsDir = File(System.getProperty("user.home"), "Projects")
            if (!projectsDir.exists()) return
            
            projectsDir.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
                createSshXml(projectDir.absolutePath, hostName, hostname, user, port, identityFile, selectedProject)
            }
        }
        
        private fun createSshXml(projectBasePath: String, hostName: String, hostname: String, user: String, port: Int, identityFile: String, selectedProject: String) {
            val ideaDir = File(projectBasePath, ".idea")
            if (!ideaDir.exists()) ideaDir.mkdirs()
            
            val sshFile = File(ideaDir, "sshConfigs.xml")
            val keyPath = if (identityFile.isNotEmpty()) "keyPath=\"\$USER_HOME\$/.ssh/id_rsa\"" else ""
            val customName = selectedProject
            
            val newConfig = "      <sshConfig host=\"$hostname\" id=\"$hostName\" $keyPath port=\"$port\" customName=\"$customName\" nameFormat=\"CUSTOM\" username=\"$user\" useOpenSSHConfig=\"true\">\n        <option name=\"customName\" value=\"$customName\" />\n      </sshConfig>"
            
            if (sshFile.exists()) {
                val content = sshFile.readText()
                if (content.contains("id=\"$hostName\"")) {
                    // Host already exists, replace it
                    val regex = Regex("<sshConfig[^>]*id=\"$hostName\"[^>]*>.*?</sshConfig>", RegexOption.DOT_MATCHES_ALL)
                    val updatedContent = content.replace(regex, newConfig.replace("\n", "\n"))
                    sshFile.writeText(updatedContent)
                } else {
                    // Add new host before closing </configs>
                    val updatedContent = content.replace("    </configs>", "$newConfig\n    </configs>")
                    sshFile.writeText(updatedContent)
                }
            } else {
                // Create new file
                val xml = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="SshConfigs">
    <configs>
$newConfig
    </configs>
  </component>
</project>"""
                sshFile.writeText(xml)
            }
        }

    }
    
    class SshHostListRenderer : DefaultListCellRenderer() {
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
                    icon = when (value.connectionStatus) {
                        ConnectionStatus.UNKNOWN -> AllIcons.General.Web
                        ConnectionStatus.TESTING -> AllIcons.Process.Step_1
                        ConnectionStatus.SUCCESS -> AllIcons.General.InspectionsOK
                        ConnectionStatus.FAILED -> AllIcons.General.Error
                    }
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