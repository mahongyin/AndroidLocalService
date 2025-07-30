package com.android.local.service.core.service

import android.content.res.AssetManager
import android.util.Log
import com.android.local.service.core.ALSHelper
import com.android.local.service.core.i.RequestListener
import org.apache.commons.fileupload.FileUpload
import org.apache.commons.fileupload.RequestContext
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.json.JSONArray
import org.json.JSONObject
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.NanoHTTPD
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newChunkedResponse
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale


class ALSService(port: Int) : NanoHTTPD(port) {

    private val TAG = this::class.java.simpleName

    private var requestListener: RequestListener? = null
    private var upload: FileUpload? = null

    private fun initUpFile() {
        val factory = DiskFileItemFactory()
        upload = FileUpload(factory)
    }

    fun setRequestListener(listener: RequestListener) {
        this.requestListener = listener
    }

    @Deprecated("Deprecated in Java")
    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri
        var params = session.parms
        //Log.d("headers", "${session.headers}")

        var contentType = "text/plain"
        if (session.headers.containsKey("content-type")) {
            contentType = session.headers["content-type"]?.toLowerCase(Locale.getDefault())
                ?: "text/plain"
        }
        if (Method.POST == method) {
            try {
                if (contentType.contains("multipart/form-data")) {// TODO 不太支持
                    params = handleMultipartData(session)
                } else if (contentType.contains("application/json")) {  // 如果是 JSON 请求
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
        val response = requestListener?.onRequest(contentType, action, params ?: mapOf())
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

    private fun handleMultipartData(session: IHTTPSession): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val filesInfo = mutableMapOf<String, MutableList<Map<String, Any>>>()
        val formFields = mutableMapOf<String, String>()
        try {
            val request = object : RequestContext {
                override fun getCharacterEncoding(): String {
                    return session.headers?.get("content-type")?.trim()?.split(";")
                        ?.get(1)?.split("=")?.get(1) ?: "UTF-8"
                }

                override fun getContentType(): String {
                    return session.headers?.get("content-type") ?: "text/plain"
                }

                override fun getContentLength(): Int {
                    return session.headers?.get("content-length")?.toInt() ?: 0
                }

                override fun getInputStream(): InputStream {
                    return session.inputStream
                }
            }
            if (upload == null) {
                initUpFile()
            }
            val items = upload?.parseRequest(request)
            val listFile = arrayListOf<Map<String, Any>>()
            items?.forEach { item ->
                if (item.isFormField) {
                    val value = item.getString("UTF-8")
                    //Log.d("formField:", value)
                    // 普通表单字段
                    formFields[item.fieldName] = value
                } else {//自动识别是文件还是普通字段
                    // 文件上传字段
                    val fileName = item.name
                    val fieldName = item.fieldName
                    val contentType = item.contentType
                    val fileSize = item.size

                    // 保存文件到临时目录
                    val context = ALSHelper.context
                    val cacheDir = context?.externalCacheDir ?: context?.cacheDir
                    val uploadDir = File(cacheDir, "uploads")
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs()
                    }
                    // 文件名
                    val uploadedFile =
                        File(uploadDir, fileName ?: "upload_${System.currentTimeMillis()}")
                    item.write(uploadedFile)

                    // 创建文件信息映射
                    val fileInfo = mapOf(
                        "path" to uploadedFile.absolutePath,
                        "filename" to (fileName ?: ""),
                        "content_type" to (contentType ?: ""),
                        "size" to fileSize,
                        "field_name" to fieldName
                    )
                    // 实现可多个文件
                    listFile.add(fileInfo)
                    // fieldName 请求端的文件字段
                }
            }
            result.putAll(formFields)

            listFile.forEach {
                val fieldName = it["field_name"] as String
                if (filesInfo.containsKey(fieldName) && filesInfo[fieldName] != null) {
                    filesInfo[fieldName]?.add(it)
                } else {
                    filesInfo[fieldName] = arrayListOf(it)
                }
            }
            filesInfo.forEach {
                result[it.key] = listToJSONArray(it.value).toString()
            }
            //if (listFile.isNotEmpty()) {
                //result["uploaded_files"] = listToJSONArray(listFile).toString()
                //result["uploaded_files"] = mapToJSONObject(filesInfo).toString()
            //}
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error handling multipart data: ${e.message}")
        }

        return result
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
                map[key] = jsonToMap(value)
            } else if (value is JSONArray) {
                map[key] = jsonArrayToList(value)
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