package com.android.local.service.core.service

import android.content.res.AssetManager
import android.util.Log
import com.android.local.service.core.ALSHelper
import com.android.local.service.core.i.RequestListener
import org.json.JSONArray
import org.json.JSONObject
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.NanoHTTPD
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newChunkedResponse
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import java.io.IOException
import java.util.Locale


class ALSService(port: Int) : NanoHTTPD(port) {
    private val TAG = this::class.java.simpleName

    private var requestListener: RequestListener? = null

    fun setRequestListener(listener: RequestListener) {
        this.requestListener = listener
    }

    override fun serve(session: IHTTPSession): Response {

        val method = session.method
        val uri = session.uri
        var params = session.parms
        Log.d("headers", "${session.headers}")
        val contentType = session.headers["content-type"] ?: "text/plain"
        if (Method.POST == method) {
            try {
                // 如果是 JSON 请求
                if (contentType.contains("application/json")) {
                    // 直接从 inputStream 读取 body
                    val body = getRequestBody(session)
                    Log.d("JSON Body", body) // 将 JSON 转换为 Map
                    params = mutableMapOf("json" to body)
                } else {
                    // 传统的表单数据处理
                    session.parseBody(mapOf())
                    params = session.parms
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // 默认传入的url是以“/”开头的，需要删除掉，否则就变成了绝对路径
        val action = uri?.substring(1) ?: return wrapResponse(session, fileNotFoundResponse())

        Log.d(TAG, "uri = $uri   method = $method   params = $params")
        val response = requestListener?.onRequest(/*contentType,*/ action, params ?: mapOf())
            ?: error("setRequestListener方法没有设置")
        return wrapResponse(session, response)
    }

    private fun getRequestBody(session: IHTTPSession): String {
        val contentLength = session.headers?.get("content-length")?.toInt() ?: 0
        val buffer = ByteArray(contentLength)
        try {
            session.inputStream.read(buffer, 0, contentLength)
            return String(buffer, Charsets.UTF_8)
        } catch (e: IOException) {
            e.printStackTrace()
            return ""
        }
    }


    private fun wrapResponse(session: IHTTPSession, response: Response): Response {
        //下面是跨域的参数
        var allowHeaders = ""
        session.headers?.let {
            val requestHeaders = it["access-control-request-headers"]
            allowHeaders = requestHeaders ?: "Content-Type"
        }
        response.addHeader("Access-Control-Allow-Headers", allowHeaders);
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Max-Age", "" + 42 * 60 * 60);
        return response
    }

    private fun getFileExtensionName(fileName: String): String {
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.getDefault())
    }

    private fun jsonResponse(code: Int, message: String, data: Any?): Response {
        val result = hashMapOf<String, Any>()
        result["code"] = code
        result["message"] = message
        data?.let {
            result["data"] = it
        }
        return newFixedLengthResponse(
            Status.OK,
            mimeTypes()["json"],
            mapToJsonString(result)
        )
    }

    private fun fileResponse(fileName: String): Response {
        val context = ALSHelper.context ?: return fileNotFoundResponse()
        val assetManager: AssetManager = context.assets
        return try {
            val stream = assetManager.open(fileName)
            val extension: String = getFileExtensionName(fileName)
            newChunkedResponse(Status.OK, mimeTypes()[extension], stream)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "File not exist. $fileName")
            fileError()
        }
    }

    private fun fileNotFoundResponse(error: String = "Error 404, file not found."): Response {
        return newFixedLengthResponse(
            Status.NOT_FOUND,
            MIME_PLAINTEXT,
            error
        )
    }

    fun success(data: Any) = jsonResponse(code = 200, message = "success", data = data)
    fun successEmpty() = jsonResponse(code = 200, message = "success", data = null)
    fun error(message: String) = jsonResponse(code = 500, message = message, data = null)
    fun errorPath() =
        jsonResponse(code = 500, message = "unknow request path, please check it", data = null)

    fun fileSuccess(fileName: String) = fileResponse(fileName)
    fun fileError() = fileNotFoundResponse()


    /*****************************************************************/


    private fun jsonStringToMap(json: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        val jsonObject = JSONObject(json)
        for (key in jsonObject.keys()) {
            map[key] = jsonObject.getString(key)
        }
        return map
    }

    private fun jsonToMap(jsonObject: JSONObject): Map<String, Any> {
        val map: MutableMap<String, Any> = HashMap()
        for (key in jsonObject.keys()) {
            val value = jsonObject[key]
            if (value is JSONObject) {
                map[key] = jsonToMap(value as JSONObject)
            } else if (value is JSONArray) {
                map[key] = jsonArrayToList(value as JSONArray)
            } else {
                map[key] = value
            }
        }
        return map
    }

    private fun jsonArrayToList(jsonArray: JSONArray): List<Any> {
        val list: MutableList<Any> = ArrayList()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray[i]
            if (value is JSONObject) {
                list.add(jsonToMap(value))
            } else if (value is JSONArray) {
                list.add(jsonArrayToList(value))
            } else {
                list.add(value)
            }
        }
        return list
    }

    private fun mapToJsonString(map: Map<String, Any>): String {
        // 方法1: 直接使用Map构造JSONObject
        val jsonObject = JSONObject(map)
        return jsonObject.toString()
    }

    private fun mapToJsonString2(map: Map<String, Any>): String {
        // 方法2: 手动遍历添加
        val jsonObject2 = JSONObject()
        for ((key, value) in map.entries) {
            jsonObject2.put(key, value)
        }
        return jsonObject2.toString()
    }

    private fun mapToJSONObject(map: Map<String, Any>): JSONObject {
        val jsonObject = JSONObject()
        for ((key, value) in map) {
            if (value is Map<*, *>) {
                // 递归处理嵌套Map
                jsonObject.put(key, mapToJSONObject(value as Map<String, Any>))
            } else if (value is List<*>) {
                // 处理List
                jsonObject.put(key, listToJSONArray(value))
            } else {
                // 处理基本类型
                jsonObject.put(key, value)
            }
        }
        return jsonObject
    }

    private fun listToJSONArray(list: List<*>): JSONArray {
        val jsonArray = JSONArray()
        for (item in list) {
            if (item is Map<*, *>) {
                jsonArray.put(mapToJSONObject(item as Map<String, Any>))
            } else if (item is List<*>) {
                jsonArray.put(listToJSONArray(item))
            } else {
                jsonArray.put(item)
            }
        }
        return jsonArray
    }
}