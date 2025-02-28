package com.zj.gyl.windy.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader.getIcon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.zj.gyl.windy.services.entity.Constants
import com.zj.gyl.windy.toolWindow.windows.LoginUIWindow
import com.zj.gyl.windy.toolWindow.windows.WindyUIWindow
import java.beans.PropertyChangeEvent
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.UIManager


class WindyToolWindowFactory : ToolWindowFactory {
    private var isListenerAdded = false
    private val lightIcon: Icon = getIcon("/icons/white-logo.png", WindyToolWindowFactory::class.java)
    private val darkIcon: Icon = getIcon("/icons/dark-logo.png", WindyToolWindowFactory::class.java)

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getIconForCurrentTheme(): Icon {
        // 获取当前主题
        val theme = UIManager.getLookAndFeel().name
        return if ("Dark" == theme || "Darcula" == theme) {
            lightIcon
        } else {
            darkIcon
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val propertiesComponent = PropertiesComponent.getInstance()
        val token = propertiesComponent.getValue(Constants.WINDY_TOKEN_KEY)
        val window: JPanel = if (token == null || validateTokenWithServer(token)) {
            LoginUIWindow(toolWindow).getContent()
        } else {
            WindyUIWindow(toolWindow).getContent()
        }
        val content = ContentFactory.getInstance().createContent(window, null, false)
        toolWindow.contentManager.addContent(content)

        if (!isListenerAdded) {
            addThemeChangeListener(toolWindow);
            isListenerAdded = true;
        }
        toolWindow.setIcon(getIconForCurrentTheme());
    }

    private fun addThemeChangeListener(toolWindow: ToolWindow) {
        UIManager.addPropertyChangeListener { evt: PropertyChangeEvent ->
            if ("lookAndFeel" == evt.propertyName) {
                // 当主题变化时，更新图标
                toolWindow.setIcon(getIconForCurrentTheme())
            }
        }
    }

    private fun validateTokenWithServer(token: String): Boolean {
        return "" == token
    }

    override fun shouldBeAvailable(project: Project) = true
}
