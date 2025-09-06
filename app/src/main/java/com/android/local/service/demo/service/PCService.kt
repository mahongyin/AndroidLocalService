package com.android.local.service.demo.service

import com.android.local.service.BuildConfig
import com.android.local.service.annotation.Page
import com.android.local.service.annotation.Request
import com.android.local.service.annotation.RequestHeader
import com.android.local.service.annotation.ServicePort
import com.android.local.service.annotation.UpFile
import com.android.local.service.annotation.UpJson
import com.android.local.service.annotation.UpXml
import com.android.local.service.core.e.CustomResponse
import com.android.local.service.core.service.ALSService
import com.android.local.service.demo.livedata.LiveDataHelper
import java.util.UUID

@ServicePort(port = 2222)
abstract class PCService {

    @Page("index")
    fun getIndexFileName() = "test_page.html"

    @Request("saveXml")
    fun saveXml(@RequestHeader() requestHeader: Map<String, String>, @UpXml xml2: String): String {
        // 很另类 使用主动抛出异常 实现自定义响应, contentType默认json
        val responseHeader = mapOf(
            //"Content-Type" to "application/xml",
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "POST, GET, OPTIONS, DELETE, PUT, PATCH, HEAD",
            "Access-Control-Allow-Headers" to "Content-Type, Authorization, X-Requested-With",
        )
        val customResponse = CustomResponse(responseHeader, ALSService.mimeTypes().get("xml"))
        customResponse.headers = responseHeader
        throw customResponse
        return xml2;
    }

    @Request("custom")
    fun customResponse() {
        // 很另类 使用主动抛出异常 实现自定义响应json
        throw CustomResponse(listOf<String>())
    }
    @Request("saveData")
    fun saveData(content: String): String {
        LiveDataHelper.saveDataLiveData.postValue(content + UUID.randomUUID());
        return content;
    }

    /** 如果 application/json 参数必须@UpJson
     *  json = { "key1": "value", "key2":{}, "key3":[], "key4":111 }
     */
    @Request("saveJson")
    fun saveJson(@UpJson json2: String, name: String): String {
        LiveDataHelper.saveDataLiveData.postValue(json2);
        return json2;
    }

    // 上传文件不用给文件留字段
    @Request("saveFile")
    fun saveFile(@UpFile file: String, @UpFile file2: String, name: String, type: String): String {
        //Log.d("form-data", file.toString() + name + type)
        return file;
    }

    @Request("queryAppInfo")
    fun getAppInfo(): HashMap<String, Any> {
        return hashMapOf(
            "applicationId" to BuildConfig.APPLICATION_ID,
            "versionName" to BuildConfig.VERSION_NAME,
            "versionCode" to BuildConfig.VERSION_CODE,
            "uuid" to UUID.randomUUID(),
        )
    }
}