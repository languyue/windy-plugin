package com.zj.gyl.windy.toolWindow.windows

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.wm.ToolWindow
import com.zj.gyl.windy.services.WindyApplicationService
import com.zj.gyl.windy.services.entity.Constants
import com.zj.gyl.windy.services.entity.LoginParam
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.*
import javax.swing.*

class LoginUIWindow(val toolWindow: ToolWindow) {

    private fun resizeIcon(icon: Icon, width: Int, height: Int): Icon {
        val img = (icon as ImageIcon).image
        val resizedImg = img.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        return ImageIcon(resizedImg)
    }

    fun getContent(): JPanel {
        val mainPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        // 增加填充使内容上下居中
        gbc.weightx = 1.0
        gbc.weighty = 0.1
        gbc.gridx = 0
        gbc.gridy = 0
        mainPanel.add(Box.createGlue(), gbc)

        addImageIcon(gbc, mainPanel)

        // 表单部分设置为水平居中
        val (nameField, passwordField, confirmButton) = addLoginForm(gbc, mainPanel)

        // 设置按钮放置在底部右下角，宽度保持为50
        addSettingButton(gbc, mainPanel, confirmButton, nameField, passwordField)
        return mainPanel
    }

    private fun addSettingButton(
        gbc: GridBagConstraints,
        mainPanel: JPanel,
        confirmButton: JButton,
        nameField: JTextField,
        passwordField: JPasswordField
    ) {
        val settingsButton = JButton("设置")
        gbc.gridx = 1
        gbc.gridy = 4
        gbc.anchor = GridBagConstraints.SOUTHEAST
        gbc.insets = Insets(10, 10, 10, 10)
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        mainPanel.add(settingsButton, gbc)
        settingsButton.preferredSize = Dimension(50, settingsButton.preferredSize.height)

        // Confirm Button Action
        confirmButton.addActionListener {
            val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
            if (Objects.isNull(server)) {
                JOptionPane.showMessageDialog(mainPanel, "服务地址未设置，请点击设置按钮配置")
                return@addActionListener
            }

            if (authenticateUser(nameField.text, String(passwordField.password))) {
                PropertiesComponent.getInstance().setValue(Constants.WINDY_USER_KEY, nameField.text)
                switchToBusinessPage()
            } else {
                JOptionPane.showMessageDialog(mainPanel, "登录失败，请重试")
            }
        }

        // Settings Button Action
        settingsButton.addActionListener {
            showSettingsDialog()
        }
    }

    private fun addLoginForm(
        gbc: GridBagConstraints,
        mainPanel: JPanel
    ): Triple<JTextField, JPasswordField, JButton> {
        val formPanel = JPanel(GridBagLayout())
        val formGbc = GridBagConstraints()
        formGbc.insets = Insets(5, 5, 5, 5)
        formGbc.fill = GridBagConstraints.NONE  // 表单不填满整个面板
        formGbc.anchor = GridBagConstraints.CENTER  // 水平居中

        formGbc.gridx = 0
        formGbc.gridy = 0
        formPanel.add(JLabel("用户"), formGbc)

        formGbc.gridx = 1
        val nameField = JTextField(20)
        val userName = PropertiesComponent.getInstance().getValue(Constants.WINDY_USER_KEY)
        if (userName != null && userName != ""){
            nameField.text = userName
        }
        formPanel.add(nameField, formGbc)

        formGbc.gridx = 0
        formGbc.gridy = 1
        formPanel.add(JLabel("密码"), formGbc)

        formGbc.gridx = 1
        val passwordField = JPasswordField(20)
        formPanel.add(passwordField, formGbc)

        formGbc.gridx = 0
        formGbc.gridy = 2
        formGbc.gridwidth = 2
        formGbc.fill = GridBagConstraints.NONE
        formGbc.anchor = GridBagConstraints.CENTER
        val confirmButton = JButton("确认")
        formPanel.add(confirmButton, formGbc)

        gbc.gridy = 2
        gbc.gridx = 0
        gbc.gridwidth = 2  // 跨越两列以确保居中
        gbc.anchor = GridBagConstraints.CENTER  // 水平居中
        gbc.insets = Insets(5, 10, 5, 10)
        gbc.fill = GridBagConstraints.NONE  // 不填充水平方向空间
        mainPanel.add(formPanel, gbc)

        // 增加填充使内容上下居中
        gbc.weightx = 1.0
        gbc.weighty = 0.1
        gbc.gridx = 0
        gbc.gridy = 3
        mainPanel.add(Box.createGlue(), gbc)
        return Triple(nameField, passwordField, confirmButton)
    }

    private fun addImageIcon(gbc: GridBagConstraints, mainPanel: JPanel) {
        // Image row
        val imageLabel = JLabel()
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 2  // 跨越两列以确保居中
        gbc.anchor = GridBagConstraints.CENTER  // 水平居中
        gbc.fill = GridBagConstraints.NONE  // 不填充水平空间
        gbc.insets = Insets(10, 10, 10, 10)
        mainPanel.add(imageLabel, gbc)
        // 添加窗口大小监听器以调整图片大小
        mainPanel.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                val with = mainPanel.width / 3
                val image = resizeIcon(ImageIcon(javaClass.classLoader.getResource("icons/windy.png")), with, with)
                imageLabel.icon = image
            }
        })
    }

    private fun authenticateUser(username: String, password: String): Boolean {
        val windyService = ApplicationManager.getApplication().getService(WindyApplicationService::class.java)
        val result = windyService.userLogin(LoginParam(username, password))
        val isSuccess = Objects.nonNull(result) && Objects.nonNull(result!!.token)
        if (isSuccess){
            thisLogger().info("user login success: $username")
            PropertiesComponent.getInstance().setValue(Constants.WINDY_TOKEN_KEY, result!!.token)
        }
        return isSuccess
    }

    private fun switchToBusinessPage() {
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(WindyUIWindow(toolWindow).getContent(), null, false)
        contentManager.removeAllContents(true)
        contentManager.addContent(content)
    }

    private fun showSettingsDialog() {
        val dialog = JDialog()
        dialog.title = "服务配置"
        dialog.isModal = true

        // 设置弹框的大小
        dialog.setSize(500, 250)
        dialog.setLocationRelativeTo(null) // 窗口居中

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        // 主要内容区域（居中显示）
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = Insets(10, 10, 10, 10)
        val contentPanel = JPanel(GridBagLayout())
        val contentGbc = GridBagConstraints()
        contentGbc.insets = Insets(5, 5, 5, 5)
        contentGbc.anchor = GridBagConstraints.CENTER

        contentGbc.gridx = 0
        contentGbc.gridy = 0
        contentPanel.add(JLabel("服务地址"), contentGbc)

        contentGbc.gridx = 1
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val addressField = JTextField(30)
        addressField.text = server
        contentPanel.add(addressField, contentGbc)

        gbc.anchor = GridBagConstraints.CENTER
        panel.add(contentPanel, gbc)

        // 底部确认按钮
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 1.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.SOUTH
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val confirmButton = JButton("确认")
        buttonPanel.add(confirmButton)
        panel.add(buttonPanel, gbc)

        confirmButton.addActionListener {
            var server = addressField.text
            if (server.endsWith("/")) {
                server = server.substring(0, server.length -1)
            }
            PropertiesComponent.getInstance().setValue(Constants.WINDY_SERVER_KEY, server)
            dialog.dispose()
        }

        dialog.contentPane.add(panel)
        dialog.isVisible = true
    }
}