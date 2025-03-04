package com.zj.gyl.windy.toolWindow.windows

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.zj.gyl.windy.services.DataLoadListener
import com.zj.gyl.windy.services.WindyApplicationService
import com.zj.gyl.windy.services.entity.Constants
import com.zj.gyl.windy.services.entity.StatusType
import com.zj.gyl.windy.services.entity.WorkTask
import com.zj.gyl.windy.toolWindow.CustomNode
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.stream.Collectors
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel


class WindyUIWindow(private val toolWindow: ToolWindow) {
    private val project: Project = toolWindow.project
    private var selectedNode: DefaultMutableTreeNode? = null
    private var parentNode: DefaultMutableTreeNode? = null
    private var globalTree: JTree? = null
    private var dataMap: HashMap<String, Any> = HashMap()
    fun getContent() = JBPanel<JBPanel<*>>().apply {
        layout = BorderLayout()

        //创建树节点
        val root = DefaultMutableTreeNode("Windy")
        val workPlat = DefaultMutableTreeNode("个人工作台")
        val demandNode = DefaultMutableTreeNode("需求")
        val bugNode = DefaultMutableTreeNode("缺陷")
        val workNode = DefaultMutableTreeNode("任务")
        val pipeline = DefaultMutableTreeNode("流水线")
        workPlat.add(demandNode)
        workPlat.add(bugNode)
        workPlat.add(workNode)
        root.add(workPlat)
        root.add(pipeline)

        val tree = JTree(root)
        globalTree = tree
        tree.isOpaque = false
        tree.background = Color(0, 0, 0, 0)
        bindSubTreeNode(tree, demandNode, bugNode, workNode, pipeline)
        tree.cellRenderer = CustomTreeCellRenderer()

        //绑定右侧菜单
        bindRightMenu(tree)
        val scrollPane = JScrollPane(tree)
        scrollPane.border = BorderFactory.createEmptyBorder()
        background = UIUtil.getPanelBackground()
        add(scrollPane, BorderLayout.CENTER)

        //开始添加底部按钮
        val (addWorkBtn, btnRefresh) = addBottomButtons(toolWindow)
        addWorkBtn.addActionListener {
            //新增创建工作项的表单
            createWorkFormUI(tree, demandNode, bugNode, workNode, pipeline)
        }
        btnRefresh.addActionListener {
            //点击刷新按钮，重新绑定下树得到子节点
            bindSubTreeNode(tree, demandNode, bugNode, workNode, pipeline)

        }
    }

    class CustomTreeCellRenderer : DefaultTreeCellRenderer() {
        private fun resizeIcon(icon: Icon, width: Int, height: Int): Icon {
            val img = (icon as ImageIcon).image
            val resizedImg = img.getScaledInstance(width, height, Image.SCALE_SMOOTH)
            return ImageIcon(resizedImg)
        }

        private val parentIconMap: Map<String, Icon> = mapOf(
            "缺陷" to resizeIcon(ImageIcon(javaClass.classLoader.getResource("icons/bug.png")), 12, 12),
            "需求" to resizeIcon(ImageIcon(javaClass.classLoader.getResource("icons/demand.png")), 12, 12),
            "任务" to resizeIcon(ImageIcon(javaClass.classLoader.getResource("icons/task.png")), 12, 12)
        )

        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            if (leaf) {
                component.colorModel
                if (component is JLabel && value is DefaultMutableTreeNode) {
                    value?.let {
                        val parentNode = value.parent as? DefaultMutableTreeNode
                        val parentName = ((parentNode?.userObject as? String) ?: "任务").split(" ")[0]
                        val icon = parentIconMap[parentName]
                        component.icon = icon
                        if (parentIconMap.keys.contains(value.userObject as? String)) {
                            component.icon = openIcon
                        }
                        if (it is CustomNode && it.loading){
                            component.text = "[运行中] ${it.name}"
                            component.toolTipText = "[${it.name}]流水线正在运行中..."
                        }else{
                            component.toolTipText = "[${it}]流水线未运行"
                        }
                    }

                }
            }
            return component
        }
    }

    private fun JBPanel<JBPanel<*>>.addBottomButtons(toolWindow: ToolWindow): Pair<JButton, JButton> {
        val panel = JPanel(GridBagLayout())
        val addWorkBtn = JButton("+ 新增工作项")
        val btnRefresh = JButton("刷新")

        // Create a popup menu
        val popupMenu = JPopupMenu().apply {
            add(JMenuItem("退出登录")).addActionListener {
                // Handle logout action
                PropertiesComponent.getInstance().setValue(Constants.WINDY_TOKEN_KEY, "")
                switchToBusinessPage()
            }
        }

        val popupButton = JButton("⚙\uFE0F 操作").apply {
            addActionListener {
                popupMenu.show(this, 0, -popupMenu.preferredSize.height) // Position above the button
            }
        }

        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.gridx = 1
        gbc.gridy = 0
        panel.add(btnRefresh, gbc)

        gbc.weightx = 0.0
        gbc.gridx = 2
        gbc.gridy = 0
        panel.add(popupButton, gbc)

        gbc.weightx = 0.0
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(addWorkBtn, gbc)

        add(panel, BorderLayout.SOUTH)
        return Pair(addWorkBtn, btnRefresh)
    }


    private fun createWorkFormUI(
        tree: JTree,
        demandNode: DefaultMutableTreeNode,
        bugNode: DefaultMutableTreeNode,
        workNode: DefaultMutableTreeNode,
        pipeline: DefaultMutableTreeNode
    ) {
        val ideFrame = WindowManager.getInstance().getIdeFrame(project)
        val frame = ideFrame!!.component as? JFrame

        val dialog = JDialog(frame, "新增工作项", true)
        dialog.layout = GridBagLayout()
        val gbc = GridBagConstraints()

        // 设置默认权重和填充
        gbc.insets = JBUI.insets(5)
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.NORTH

        // 第一行：工作项名称和输入框
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        dialog.add(JLabel("工作项名称:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        val nameField = JTextArea(4, 20) // 设定较大的输入框
        val scrollNamePane = JScrollPane(nameField)
        dialog.add(scrollNamePane, gbc)

        // 第二行：关联需求/缺陷ID和下拉框
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        dialog.add(JLabel("关联需求/缺陷ID:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        class SelectItem(var id: String, var name: String) {
            override fun toString(): String {
                return name
            }
        }

        val windyService = ApplicationManager.getApplication().getService(WindyApplicationService::class.java)
        val items = windyService.demandPage!!.getList().stream().map {
            SelectItem(it.demandId, it.demandName)
        }.collect(Collectors.toList())
        val bugItems = windyService.bugPage!!.getList().stream().map {
            SelectItem(it.bugId, it.bugName)
        }.collect(Collectors.toList())
        items.addAll(bugItems)

        val idComboBox = JComboBox(items.toTypedArray())
        dialog.add(idComboBox, gbc)

        // 第三行：工作项描述和输入框
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        dialog.add(JLabel("工作项描述:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        val descriptionField = JTextArea(5, 20) // 设定较大的输入框
        dialog.add(JScrollPane(descriptionField), gbc)

        // 添加确认和取消按钮
        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout(FlowLayout.RIGHT)
        val okButton = JButton("确认")
        val cancelButton = JButton("取消")

        buttonPanel.add(okButton)
        buttonPanel.add(cancelButton)

        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.EAST
        gbc.fill = GridBagConstraints.NONE
        dialog.add(buttonPanel, gbc)

        // 设置按钮点击事件
        okButton.addActionListener {
            // 处理确认操作
            val name = nameField.text
            val description = descriptionField.text
            val selectedItem = idComboBox.selectedItem as? SelectItem
            if (selectedItem == null) {
                showNotification("提示", "关联需求/缺陷Id不能为空", NotificationType.ERROR)
            } else {
                val result = windyService.createWork(WorkTask(description, name, selectedItem!!.id))
                if (result) {
                    showNotification("提示", "添加任务成功!")
                    dialog.dispose()
                    bindSubTreeNode(tree, demandNode, bugNode, workNode, pipeline)
                } else {
                    showNotification("提示", "创建工作项任务失败", NotificationType.ERROR)
                }
            }

        }

        cancelButton.addActionListener {
            dialog.dispose()
        }
        dialog.setSize(600, 400) // 设置宽度为 600，高度可以根据需要调整
        dialog.setLocationRelativeTo(null) // 确保对话框在屏幕中央显示
        dialog.isVisible = true
    }

    private fun bindRightMenu(tree: JTree) {
        val windyService = ApplicationManager.getApplication().getService(WindyApplicationService::class.java)
        windyService.load()

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                super.mouseClicked(e);
                var x = e!!.x
                var y = e.y
                if (e.clickCount == 2) {
                    //双击加载流水线
                    dbClickLoadPipelines(tree, e, windyService)
                }

                if (e.getButton() == MouseEvent.BUTTON3) {
                    val menu = JPopupMenu()
                    var location = tree.getPathForLocation(e!!.x, e.y)
                    selectedNode = location?.lastPathComponent as? DefaultMutableTreeNode
                    parentNode = selectedNode?.parent as? DefaultMutableTreeNode
                    if (selectedNode?.isLeaf == true) {
                        menu.removeAll()
                        //右键切换需求、缺陷、工作项状态
                        if(isSpaceRightMenu(parentNode?.userObject.toString())){
                            exchangeSpaceStatus(tree, x, y, menu)
                        }

                        //右键切换执行和查询流水线状态
                        var pipelineNode = selectedNode?.parent?.parent as? DefaultMutableTreeNode
                        if(isPipelineRightMenu(pipelineNode?.userObject.toString())){
                            exchangePipelineStatus(selectedNode!!, menu)
                        }
                        tree.setSelectionPath(location);
                        menu.show(tree, x, y);
                    }
                }
            }
        })
    }

    private fun exchangePipelineStatus(currentNode: DefaultMutableTreeNode, menu: JPopupMenu) {
        val windyService = ApplicationManager.getApplication().getService(WindyApplicationService::class.java)
        val runMenu = JMenuItem("运行流水线")
        runMenu.addActionListener {
            currentNode.let {
                var pipeline = it as CustomNode
                var result = windyService.runPipeline(pipeline.relatedId)
                if (result) {
                    showNotification("流水线: ${it}", "运行流水线成功!")
                } else {
                    showError("流水线: ${it}", "运行流水线失败!")
                }
            }
        }
        val refreshMenu = JMenuItem("刷新状态")
        refreshMenu.addActionListener {
            currentNode.let {
                var pipeline = it as CustomNode
                var pipelineStatus = windyService.getPipelineStatus(pipeline.relatedId)
                it.loading = pipelineStatus.status == 4
                if (Objects.nonNull(pipelineStatus)) {
                    showNotification("流水线: ${it}", "状态刷新成功!")
                } else {
                    showError("流水线: ${it}", "状态刷新失败!")
                }
            }
        }
        menu.add(runMenu)
        menu.add(refreshMenu)
    }

    private fun exchangeSpaceStatus(tree: JTree, x: Int, y: Int, menu: JPopupMenu) {
        val changeMenu = JMenuItem("切换状态")
        changeMenu.addActionListener {
            parentNode?.let {
                val windyService = ApplicationManager.getApplication().getService(WindyApplicationService::class.java)
                val parentName = it.userObject.toString()
                val statusMenu = JPopupMenu()
                var statusList: List<StatusType> = ArrayList()
                if (parentName.contains("需求")) {
                    statusList = windyService.demandStatusList
                }
                if (parentName.contains("缺陷")) {
                    statusList = windyService.bugStatusList
                }
                if (parentName.contains("任务")) {
                    statusList = windyService.workStatusList
                }

                statusList.forEach {
                    val menuItem = JMenuItem(it.statusName)
                    menuItem.addActionListener {
                        val itemName = (it.source as JMenuItem).text
                        selectedNode?.let {
                            val customNode = it as? CustomNode
                            if (parentName.contains("需求")) {
                                val status = exchangeStatusValue(windyService.demandStatusList, itemName)
                                val result = windyService.updateDemandStatus(customNode?.relatedId.toString(), status!!)
                                showNotification("需求","[${customNode?.name}]状态切换${if (result) "成功" else "失败"}")
                            }
                            if (parentName.contains("缺陷")) {
                                val status = exchangeStatusValue(windyService.bugStatusList, itemName)
                                val result = windyService.updateBugStatus(customNode?.relatedId.toString(), status!!)
                                showNotification("缺陷","[${customNode?.name}]状态切换${if (result) "成功" else "失败"}")
                            }
                            if (parentName.contains("任务")) {
                                val status = exchangeStatusValue(windyService.workStatusList, itemName)
                                val result = windyService.updateWorkStatus(customNode?.relatedId.toString(), status!!)
                                showNotification("任务","[${customNode?.name}]状态切换${if (result) "成功" else "失败"}")
                            }
                            windyService.load()
                        }
                    }
                    statusMenu.add(menuItem)
                }
                statusMenu.show(tree, x, y)
            }
        }
        menu.add(changeMenu)
    }

    private fun dbClickLoadPipelines(
        tree: JTree,
        e: MouseEvent,
        windyService: WindyApplicationService
    ) {
        var location = tree.getPathForLocation(e!!.x, e.y)
        var currentNode = location?.lastPathComponent as? DefaultMutableTreeNode
        var parentNode = currentNode?.parent as? DefaultMutableTreeNode
        if (currentNode?.isLeaf == true && isPipelineRightMenu(parentNode?.userObject.toString())) {
            currentNode.let {
                val customNode = it as? CustomNode
                customNode?.relatedId?.let { it1 ->
                    windyService.asyncPipelineData(it1, object : DataLoadListener {
                        override fun load() {
                            windyService.pipelineList!!.forEach {
                                currentNode.add(
                                    CustomNode(
                                        it.pipelineName, it.pipelineId, it.status == 4
                                    )
                                )
                            }
                            reloadNode(tree, currentNode)
                        }

                        override fun expire() {
                            TODO("Not yet implemented")
                        }
                    })
                }
            }
        }
    }

    private fun reloadNode(tree: JTree, currentNode: DefaultMutableTreeNode) {
        val model = tree.model as DefaultTreeModel
        model.nodeStructureChanged(currentNode)
    }

    private fun isSpaceRightMenu(text: String) = text.contains("需求") ||text.contains("缺陷")
            || text.contains("任务")

    private fun isPipelineRightMenu(text: String) = text.contains("流水线")

    private fun exchangeStatusValue(statusList: List<StatusType>, menuName: String): Int? {
        statusList.forEach {
            if (it.statusName == menuName) {
                return it.value
            }
        }
        return null
    }

    private fun exchangeStatusName(statusList: List<StatusType>, value: Int): String? {
        statusList.forEach {
            if (it.value == value) {
                return "[" + it.statusName + "] "
            }
        }
        return ""
    }

    fun showNotification(title: String, tip: String) {
        showNotification(title, tip, NotificationType.INFORMATION)
    }

    fun showError(title: String, tip: String) {
        showNotification(title, tip, NotificationType.ERROR)
    }

    fun showNotification(title: String, tip: String, notifyType: NotificationType) {
        var titleName = title
        if (title.length > 19) {
            titleName = title.substring(0, 19) + ".."
        }
        val notificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("Windy Notification Group")
        val notification: Notification = notificationGroup.createNotification(titleName, tip, notifyType)
        notification.notify(project)
    }

    private fun bindSubTreeNode(
        tree: JTree,
        demandNode: DefaultMutableTreeNode,
        bugNode: DefaultMutableTreeNode,
        workNode: DefaultMutableTreeNode,
        service: DefaultMutableTreeNode
    ) {
        val windyService = ApplicationManager.getApplication().getService(WindyApplicationService::class.java)
        windyService.load()

        windyService.asyncServiceData(object : DataLoadListener {
            override fun load() {
                service.removeAllChildren()
                service.userObject = "流水线 (双击展开)"
                for (item in windyService.serviceList!!) {
                    service.add(CustomNode(item.serviceName, item.serviceId))
                    dataMap.put(item.serviceId, item)
                    reloadTree(tree)
                }
            }

            override fun expire() {
                switchToBusinessPage()
            }
        })

        windyService.asyncDemandData(object : DataLoadListener {
            override fun load() {
                demandNode.removeAllChildren()
                demandNode.userObject = "需求 (${windyService.demandPage!!.total})"
                for (item in windyService.demandPage!!.getList()) {
                    println("${item.demandName} ========= ${item.status}")
                    val statusName = exchangeStatusName(windyService.demandStatusList, item.status)
                    demandNode.add(CustomNode(statusName + item.demandName, item.demandId))
                    dataMap.put(item.demandId, item)
                    reloadTree(tree)
                }
            }

            override fun expire() {
                switchToBusinessPage()
            }
        })
        windyService.asyncBugData(object : DataLoadListener {
            override fun load() {
                bugNode.removeAllChildren()
                val bugPage = windyService.bugPage ?: return
                bugNode.userObject = "缺陷 (${bugPage!!.total})"
                for (item in bugPage!!.getList()) {
                    val statusName = exchangeStatusName(windyService.bugStatusList, item.status)
                    bugNode.add(CustomNode(statusName + item.bugName, item.bugId))
                    dataMap.put(item.bugId, item)
                    reloadTree(tree)
                }
            }
            override fun expire() {
                switchToBusinessPage()
            }
        })
        windyService.asyncWorkData(object : DataLoadListener {
            override fun load() {
                workNode.removeAllChildren()
                workNode.userObject = "任务 (${windyService.workPage!!.total})"
                for (item in windyService.workPage!!.getList()) {
                    val statusName = exchangeStatusName(windyService.bugStatusList, item.status)
                    workNode.add(CustomNode(statusName + item.taskName, item.taskId))
                    dataMap.put(item.taskId, item)
                    reloadTree(tree)
                }
            }

            override fun expire() {
                switchToBusinessPage()
            }
        })
    }

    private fun reloadTree(tree: JTree) {
        val model = tree.model as DefaultTreeModel
        model.reload()
    }

    private fun switchToBusinessPage() {
        SwingUtilities.invokeLater {
            val contentManager = toolWindow.contentManager
            val content = contentManager.factory.createContent(LoginUIWindow(toolWindow).getContent(), null, false)
            contentManager.removeAllContents(true)
            contentManager.addContent(content)
        }
    }
}