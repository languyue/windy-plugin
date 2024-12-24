package com.zj.gyl.windy.listeners

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.wm.IdeFrame
import com.zj.gyl.windy.services.WindyApplicationService
import com.zj.gyl.windy.services.entity.Constants
import com.zj.gyl.windy.toolWindow.windows.LoginUIWindow
import javax.swing.JPanel

/**
 * 每次激活IDE时重新获取下用户的工作
 */
internal class WindyListener : ApplicationActivationListener {

    override fun applicationActivated(ideFrame: IdeFrame) {
        val propertiesComponent = PropertiesComponent.getInstance()
        val token = propertiesComponent.getValue(Constants.WINDY_TOKEN_KEY)
        if (token == null ) {
            thisLogger().info("token is null, not request user work");
            return
        }
        val windyService = ApplicationManager.getApplication().getService(WindyApplicationService::class.java)
        windyService.load()
        thisLogger().info("Application activated, start request user work")
    }
}
