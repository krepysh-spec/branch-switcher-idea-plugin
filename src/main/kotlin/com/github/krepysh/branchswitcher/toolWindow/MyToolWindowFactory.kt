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
        private val listModel = DefaultListModel<SshHost>()
        private val hostList = JBList(listModel)
        
        init {
            hostList.cellRenderer = SshHostRenderer()
            hostList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            refreshHosts()
        }

        fun getContent(): JComponent {
            val mainPanel = JPanel(BorderLayout())
            
            val toolbarGroup = DefaultActionGroup()
            toolbarGroup.add(AddHostToolbarAction())
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
                println("Edit clicked, selected: ${hostList.selectedValue}")
                hostList.selectedValue?.let { 
                    println("Showing edit dialog for: ${it.name}")
                    showEditHostDialog(it) 
                }
            }
            deleteItem.addActionListener { 
                println("Delete clicked, selected: ${hostList.selectedValue}")
                hostList.selectedValue?.let { 
                    println("Deleting host: ${it.name}")
                    deleteHost(it) 
                }
            }
            
            popup.add(editItem)
            popup.add(deleteItem)
            
            // Add mouse listener for right-click
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
                hosts.forEach { listModel.addElement(it) }
            }
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
                showAddHostDialog()
            }
        }
        
        inner class DuplicateHostToolbarAction : AnAction("Duplicate", "Duplicate selected SSH host", AllIcons.Actions.Copy) {
            override fun actionPerformed(e: AnActionEvent) {
                hostList.selectedValue?.let { duplicateHost(it) }
            }
            
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = hostList.selectedValue != null
            }
        }
        
        inner class DeleteHostToolbarAction : AnAction("Delete", "Delete selected SSH host", AllIcons.General.Remove) {
            override fun actionPerformed(e: AnActionEvent) {
                hostList.selectedValue?.let { deleteHost(it) }
            }
            
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = hostList.selectedValue != null
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
        
        private fun createSshConfig() {
            try {
                val sshDir = java.io.File(System.getProperty("user.home"), ".ssh")
                if (!sshDir.exists()) {
                    sshDir.mkdirs()
                }
                
                val configFile = java.io.File(sshDir, "config")
                configFile.writeText("# SSH Config\n# Add your hosts here\n\n# Example:\n# Host example\n#     HostName example.com\n#     User username\n#     Port 22\n")
                
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
            
            val nameField = JTextField(existingHost?.name ?: "", 20)
            val hostnameField = JTextField(existingHost?.hostname ?: "", 20)
            val userField = JTextField(existingHost?.user ?: "", 20)
            val portField = JTextField(existingHost?.port?.toString() ?: "", 20)
            
            val defaultIdentityPath = java.io.File(System.getProperty("user.home"), ".ssh/id_rsa")
            val identityField = JTextField(
                existingHost?.identityFile ?: if (defaultIdentityPath.exists()) defaultIdentityPath.absolutePath else "", 
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
                "Host Name:" to nameField,
                "Hostname:" to hostnameField,
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
                    saveHost(existingHost?.name, nameField.text, hostnameField.text, 
                           userField.text, portField.text, identityField.text)
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
        
        private fun saveHost(oldName: String?, name: String, hostname: String, user: String, port: String, identityFile: String) {
            val configFile = java.io.File(System.getProperty("user.home"), ".ssh/config")
            val lines = if (configFile.exists()) configFile.readLines().toMutableList() else mutableListOf()
            
            if (oldName != null) {
                val startIndex = lines.indexOfFirst { it.trim().startsWith("Host $oldName") }
                if (startIndex >= 0) {
                    var endIndex = startIndex + 1
                    while (endIndex < lines.size && (lines[endIndex].startsWith("    ") || lines[endIndex].trim().isEmpty())) {
                        endIndex++
                    }
                    repeat(endIndex - startIndex) { lines.removeAt(startIndex) }
                }
            }
            
            lines.add("")
            lines.add("Host $name")
            if (hostname.isNotEmpty()) lines.add("    HostName $hostname")
            if (user.isNotEmpty()) lines.add("    User $user")
            if (port.isNotEmpty()) lines.add("    Port $port")
            if (identityFile.isNotEmpty()) lines.add("    IdentityFile $identityFile")
            
            configFile.writeText(lines.joinToString("\n"))
        }
        
        private fun duplicateHost(host: SshHost) {
            val newName = "${host.name}_copy"
            saveHost(null, newName, host.hostname ?: "", host.user ?: "", host.port?.toString() ?: "", host.identityFile ?: "")
            refreshHosts()
        }
    }
    
    class SshHostRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            if (value is SshHost) {
                icon = AllIcons.Nodes.DataTables
                text = buildString {
                    append(value.name)
                    value.hostname?.let { append(" ($it)") }
                    value.user?.let { append(" - $it") }
                }
                border = BorderFactory.createEmptyBorder(2, 8, 2, 2)
            }
            
            return this
        }
    }
}