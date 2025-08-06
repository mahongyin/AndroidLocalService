package com.android.local.service.demo.service

import com.android.local.service.BuildConfig
import com.android.local.service.annotation.Get
import com.android.local.service.annotation.Page
import com.android.local.service.annotation.Service
import com.android.local.service.annotation.UpFile
import com.android.local.service.annotation.UpJson
import com.android.local.service.demo.livedata.LiveDataHelper
import java.util.UUID

@Service(port = 2222)
abstract class PCService {

    @Page("index")
    fun getIndexFileName() = "test_page.html"

    @Get("saveData")
    fun saveData(content: String): String {
        LiveDataHelper.saveDataLiveData.postValue(content + UUID.randomUUID());
        return content;
    }

    /** 如果 application/json 参数必须@UpJson
     *  json = { "key1": "value", "key2":{}, "key3":[], "key4":111 }
     */
    @Get("saveJson")
    fun saveJson(@UpJson json: String, name: String): String {
        LiveDataHelper.saveDataLiveData.postValue(json);
        return json;
    }

    // 上传文件不用给文件留字段
    @Get("saveFile")
    fun saveFile(@UpFile file: String, @UpFile file2: String, name: String, type: String): String {
        //Log.d("form-data", file.toString() + name + type)
        return file;
    }

    @Get("queryAppInfo")
    fun getAppInfo(): HashMap<String, Any> {
        return hashMapOf(
            "applicationId" to BuildConfig.APPLICATION_ID,
            "versionName" to BuildConfig.VERSION_NAME,
            "versionCode" to BuildConfig.VERSION_CODE,
            "uuid" to UUID.randomUUID(),
        )
    }
}