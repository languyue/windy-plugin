package com.zj.gyl.windy.services

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.zj.gyl.windy.WindyBundle
import com.zj.gyl.windy.services.entity.*
import com.zj.gyl.windy.services.exception.AuthException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager

@Service(Service.Level.APP)
class WindyApplicationService() {
    var demandPage: PageModel<DemandDto>? = PageModel(0, ArrayList())
    var bugPage: PageModel<BugDto>? = PageModel(0, ArrayList())
    var serviceList: ArrayList<ServiceDto>? =  ArrayList()
    var pipelineList: ArrayList<PipelineDto>? = ArrayList()
    var workPage: PageModel<WorkTaskDto>? = PageModel(0, ArrayList())
    var bugStatusList: List<StatusType> =  ArrayList()
    var demandStatusList: List<StatusType> =  ArrayList()
    var workStatusList: List<StatusType> =  ArrayList()

    init {
        thisLogger().info(WindyBundle.message("appService", "WIndyProjectService"))
    }

    fun load(){
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        if (server.isNullOrEmpty()){
            return
        }
        thread{
            requestBugStatus()
            requestDemandStatus()
            requestWorkStatus()
        }
    }

    fun asyncDemandData(listener: DataLoadListener) {
        thread {
            try {
                demandPage = requestDemandList()
                listener.load()
            } catch (e: AuthException) {
                listener.expire()
            }

        }
    }

    fun asyncBugData(listener: DataLoadListener) {
        thread {
            try {
                bugPage = requestBugList()
                listener.load()
            }catch (e : AuthException){
                listener.expire()
            }
        }
    }

    fun asyncServiceData(listener: DataLoadListener) {
        thread {
            try {
                serviceList = requestServiceList()
                listener.load()
            }catch (e : AuthException){
                listener.expire()
            }
        }
    }
    fun asyncPipelineData(serviceId: String, listener: DataLoadListener) {
        thread {
            try {
                var list = requestPipelineList(serviceId)
                pipelineList = list?.filter { it.pipelineType == 3} as ArrayList<PipelineDto>?
                listener.load()
            }catch (e : AuthException){
                listener.expire()
            }
        }
    }

    fun asyncWorkData(listener: DataLoadListener) {
        thread {
            try {
                workPage = requestWorkList()
                listener.load()
            } catch (e: AuthException) {
                listener.expire()
            }
        }
    }

    fun requestDemandStatus() {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/demand/statuses"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<List<StatusType?>?>() {}.type
        demandStatusList= (gson.fromJson<Any>(dataString, type) as List<StatusType>?)!!
    }

    fun requestWorkStatus() {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/work/task/statuses"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<List<StatusType?>?>() {}.type
        workStatusList= (gson.fromJson<Any>(dataString, type) as List<StatusType>?)!!
    }


    fun updateDemandStatus(demandId: String, status: Int): Boolean {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/demand"
        val gson = Gson()
        val responseModel = put(urlString, gson.toJson(DemandStatus(demandId, status)))
        return responseModel!!.data as Boolean
    }

    fun updateBugStatus(bugId: String, status: Int): Boolean {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/bug"
        val gson = Gson()
        val responseModel = put(urlString, gson.toJson(BugStatus(bugId, status)))
        return responseModel!!.data as Boolean
    }

    fun updateWorkStatus(workId: String, status: Int): Boolean {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/work/task"
        val gson = Gson()
        val responseModel = put(urlString, gson.toJson(WorkStatus(workId, status)))
        return responseModel!!.data as Boolean
    }
    fun requestBugStatus() {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/bug/statuses"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<List<StatusType?>?>() {}.type
        bugStatusList= (gson.fromJson<Any>(dataString, type) as List<StatusType>?)!!
    }

    fun userLogin(loginParam: LoginParam): LoginResult? {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val api = "$server/v1/devops/user/login"
        val gson = Gson()
        val requestBody = gson.toJson(loginParam)
        val responseModel = post(api, requestBody)
        if (Objects.isNull(responseModel)){
            return null
        }
        val dataString = gson.toJson(responseModel!!.data)
        return gson.fromJson(dataString, LoginResult::class.java)
    }

    fun createWork(work: WorkTask): Boolean {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/work/tasks"
        val gson = Gson()
        val requestBody = gson.toJson(work)
        val responseModel = post(urlString, requestBody)
        if (Objects.isNull(responseModel)){
            return false
        }
        val dataString = gson.toJson(responseModel!!.data)
        val task = gson.fromJson(dataString, WorkTaskDto::class.java)
        return task?.taskId != null
    }

    private fun requestDemandList(): PageModel<DemandDto>? {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/user/demands?size=100"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<PageModel<DemandDto?>?>() {}.type
        return gson.fromJson<Any>(dataString, type) as PageModel<DemandDto>?
    }

    private fun requestBugList(): PageModel<BugDto>? {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/user/bugs?size=100"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<PageModel<BugDto?>?>() {}.type
        return gson.fromJson<Any>(dataString, type) as PageModel<BugDto>?
    }

    private fun requestPipelineList(serviceId: String): ArrayList<PipelineDto>? {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/pipeline/services/$serviceId/status"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<ArrayList<PipelineDto?>?>() {}.type
        return gson.fromJson<Any>(dataString, type) as ArrayList<PipelineDto>?
    }

    fun runPipeline(pipelineId: String): Boolean {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/pipeline/${pipelineId}"
        val responseModel = post(urlString, "")
        return Objects.nonNull(responseModel) && Objects.nonNull(responseModel?.data)
    }

    fun getPipelineStatus(pipelineId: String): PipelineDto {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/pipeline/${pipelineId}/status"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val itemListType = object : TypeToken<PipelineDto>() {}.type
        return gson.fromJson<Any>(dataString, itemListType) as PipelineDto
    }

    private fun requestServiceList(): ArrayList<ServiceDto>? {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/services"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val itemListType = object : TypeToken<ArrayList<ServiceDto>>() {}.type
        return gson.fromJson<Any>(dataString, itemListType) as ArrayList<ServiceDto>?
    }

    private fun requestWorkList(): PageModel<WorkTaskDto>? {
        val server = PropertiesComponent.getInstance().getValue(Constants.WINDY_SERVER_KEY)
        val urlString = "$server/v1/devops/work/tasks?size=100"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<PageModel<WorkTaskDto?>?>() {}.type
        return gson.fromJson<Any>(dataString, type) as PageModel<WorkTaskDto>?
    }

    private fun get(url: String): ResponseModel? {
        var connection: HttpURLConnection? = null
        val gson = Gson()
        try {
            val url = URL(url)
            connection = url.openConnection() as HttpURLConnection
            connection!!.requestMethod = "GET"
            connection!!.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val token = PropertiesComponent.getInstance().getValue(Constants.WINDY_TOKEN_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + token)
            val responseCode = connection!!.responseCode
            return handleHttpCode(responseCode, connection, gson)
        } catch (e: AuthException) {
            throw e
        }  catch (e: java.lang.Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun put(url: String, body: String): ResponseModel? {
        val gson = Gson()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(url)
            connection = url.openConnection() as HttpURLConnection?
            connection!!.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val token = PropertiesComponent.getInstance().getValue(Constants.WINDY_TOKEN_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.doOutput = true
            connection.outputStream.use { os ->
                os.write(body.toByteArray(charset("UTF-8")))
                os.flush()
            }
            val responseCode = connection.responseCode
            return handleHttpCode(responseCode, connection, gson)
        } catch (e: AuthException) {
            throw e
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            System.err.println("create work task error")
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun post(url: String, body: String): ResponseModel? {
        val gson = Gson()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(url)
            connection = url.openConnection() as HttpURLConnection?
            connection!!.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val token = PropertiesComponent.getInstance().getValue(Constants.WINDY_TOKEN_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.doOutput = true
            connection.outputStream.use { os ->
                os.write(body.toByteArray(charset("UTF-8")))
                os.flush()
            }
            val responseCode = connection.responseCode
            return handleHttpCode(responseCode, connection, gson)
        } catch (e: AuthException) {
            throw e
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun handleHttpCode(
        responseCode: Int,
        connection: HttpURLConnection,
        gson: Gson
    ): ResponseModel? {
        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
            PropertiesComponent.getInstance().setValue(Constants.WINDY_TOKEN_KEY, "")
            throw AuthException("user token is expire, need login")
        }

        if (responseCode in 200..299) {
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                val result = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result.append(line)
                }
                return gson.fromJson(result.toString(), ResponseModel::class.java)
            }
        }


        // 非 2xx，尝试读取 errorStream
        BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
            val errorResult = reader.readText()
            val errorResponse = gson.fromJson(errorResult, ResponseModel::class.java)
            // 500 错误时弹出 IDEA 通知
            if (responseCode == 500 && errorResponse?.message?.isNotBlank() == true) {
                showNotification("Windy请求异常", errorResponse.message)
            }
        }
        return null
    }

    fun showNotification(title: String, message: String, type: NotificationType = NotificationType.ERROR) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Windy Http Group")
                .createNotification(title, message, type)
                .notify(null)
        }
    }
}
