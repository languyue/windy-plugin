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
import kotlin.concurrent.thread


@Service(Service.Level.APP)
class WindyApplicationService() {
    var demandPage: PageModel<DemandDto>? = PageModel(0, ArrayList())
    var bugPage: PageModel<BugDto>? = PageModel(0, ArrayList())
    var workPage: PageModel<WorkTaskDto>? = PageModel(0, ArrayList())
    var bugStatusList: List<StatusType> =  ArrayList()
    var demandStatusList: List<StatusType> =  ArrayList()
    var workStatusList: List<StatusType> =  ArrayList()

    init {
        thisLogger().info(WindyBundle.message("appService", "WIndyProjectService"))
    }

    fun load(){
        val server = PropertiesComponent.getInstance().getValue("windy.server")
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
                thisLogger().warn("加载完成数据了" + demandPage!!.total)
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
                thisLogger().warn("加载完成数据了" + bugPage!!.total)
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
                thisLogger().warn("加载完成数据了" + workPage!!.total)
                listener.load()
            } catch (e: AuthException) {
                listener.expire()
            }
        }
    }

    fun requestDemandStatus() {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
        val urlString = "$server/v1/devops/demand/statuses"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<List<StatusType?>?>() {}.type
        demandStatusList= (gson.fromJson<Any>(dataString, type) as List<StatusType>?)!!
    }

    fun requestWorkStatus() {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
        val urlString = "$server/v1/devops/work/task/statuses"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<List<StatusType?>?>() {}.type
        workStatusList= (gson.fromJson<Any>(dataString, type) as List<StatusType>?)!!
    }


    fun updateDemandStatus(demandId: String, status: Int): Boolean {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
        val urlString = "$server/v1/devops/demand"
        val gson = Gson()
        val responseModel = put(urlString, gson.toJson(DemandStatus(demandId, status)))
        return responseModel!!.data as Boolean
    }

    fun updateBugStatus(bugId: String, status: Int): Boolean {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
        val urlString = "$server/v1/devops/demand"
        val gson = Gson()
        val responseModel = put(urlString, gson.toJson(BugStatus(bugId, status)))
        return responseModel!!.data as Boolean
    }

    fun updateWorkStatus(bugId: String, status: Int): Boolean {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
        val urlString = "$server/v1/devops/work/task"
        val gson = Gson()
        val responseModel = put(urlString, gson.toJson(WorkStatus(bugId, status)))
        return responseModel!!.data as Boolean
    }
    fun requestBugStatus() {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
        val urlString = "$server/v1/devops/bug/statuses"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<List<StatusType?>?>() {}.type
        bugStatusList= (gson.fromJson<Any>(dataString, type) as List<StatusType>?)!!
    }

    fun userLogin(loginParam: LoginParam): LoginResult? {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
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
        val server = PropertiesComponent.getInstance().getValue("windy.server")
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
        val server = PropertiesComponent.getInstance().getValue("windy.server")
        val urlString = "$server/v1/devops/user/demands?size=100"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<PageModel<DemandDto?>?>() {}.type
        return gson.fromJson<Any>(dataString, type) as PageModel<DemandDto>?
    }

    private fun requestBugList(): PageModel<BugDto>? {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
        val urlString = "$server/v1/devops/user/bugs?size=100"
        val responseModel = get(urlString)
        val gson = Gson()
        val dataString = gson.toJson(responseModel!!.data)
        val type = object : TypeToken<PageModel<BugDto?>?>() {}.type
        return gson.fromJson<Any>(dataString, type) as PageModel<BugDto>?
    }

    private fun requestWorkList(): PageModel<WorkTaskDto>? {
        val server = PropertiesComponent.getInstance().getValue("windy.server")
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
            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                PropertiesComponent.getInstance().setValue(Constants.WINDY_TOKEN_KEY, "")
                throw AuthException("user token is expire, need login")
            }

            val result = java.lang.StringBuilder()
            BufferedReader(InputStreamReader(connection!!.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result.append(line)
                }
            }
            val responseJson = result.toString()
            val responseModel = gson.fromJson(responseJson, ResponseModel::class.java)
            return responseModel
        } catch (e: AuthException) {
            throw e
        }  catch (e: java.lang.Exception) {
            e.printStackTrace()
            System.err.println("get bug list error")
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
            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                PropertiesComponent.getInstance().setValue(Constants.WINDY_TOKEN_KEY, "")
                throw AuthException("user token is expire, need login")
            }
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                val result = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result.append(line)
                }
                return gson.fromJson(result.toString(), ResponseModel::class.java)
            }
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
            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                PropertiesComponent.getInstance().setValue(Constants.WINDY_TOKEN_KEY, "")
                throw AuthException("user token is expire, need login")
            }
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                val result = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result.append(line)
                }
                return gson.fromJson(result.toString(), ResponseModel::class.java)
            }
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


    fun getRandomNumber() = "windy" + (1..100).random()
}
