package com.android.local.service.demo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.local.service.core.ALSHelper
import com.android.local.service.core.data.ServiceConfig
import com.android.local.service.databinding.ActivityMainBinding
import com.android.local.service.demo.livedata.LiveDataHelper
import com.android.local.service.demo.service.AndroidService
import com.android.local.service.demo.service.OtherService
import com.android.local.service.demo.service.PCService
import com.android.local.service.demo.utils.getPhoneWifiIpAddress
import okhttp3.*
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient()

    private var ipAddress: String? = null

    private var androidServicePort = 1111
    private var pcServicePort = 2222

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //使用之前先初始化
        ALSHelper.init(this.applicationContext)
        ALSHelper.configResponse("code", "msg", "data")
        //启动单个服务用法
        ALSHelper.startService(ServiceConfig(AndroidService::class.java))
        //启动多个服务用法
        ALSHelper.startServices(
            listOf(
                ServiceConfig(PCService::class.java),
                //不传默认是AndroidService类中@Service注解中的端口号
                ServiceConfig(OtherService::class.java, 4444)
            )
        )
        initIpAddress()
        initView()
        pcServiceDemoTip()
        initObserver()
//        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 1000)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            if (Environment.isExternalStorageManager()) {
//                // 已经授予「所有文件访问」权限
//                return
//            }
//            try {
//                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
//                    data = Uri.parse("package:$packageName")
//                }
//                startActivityForResult(intent, 1001)
//            } catch (e: Exception) {
//                // 部分 ROM 不支持按包名定位，退回通用设置页
//                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
//                startActivityForResult(fallback, 1001)
//            }
//        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 可选
        ALSHelper.stopAllServices()
    }
    private fun pcServiceDemoTip() {
        val pcBaseUrl = "http://${ipAddress}:${pcServicePort}"
        val showH5Page = "$pcBaseUrl/index"
        val queryAppInfo = "$pcBaseUrl/queryAppInfo"
        val saveData = "$pcBaseUrl/saveData?content=我是来自PC端的数据"
        binding.tvPcServiceTip.text =
            "局域网内电脑端可输入一下地址看demo效果：\n$showH5Page\n$queryAppInfo\n$saveData"
    }

    private fun initIpAddress() {
        if (ipAddress == null) {
            ipAddress = getPhoneWifiIpAddress()
        }
        pcServicePort =
            ALSHelper.getServiceList().find { it.serviceName == PCService::class.java.simpleName }?.port
                ?: 0
        androidServicePort =
            ALSHelper.getServiceList().find { it.serviceName == AndroidService::class.java.simpleName }?.port
                ?: 0
    }

    private fun initObserver() {
        LiveDataHelper.saveDataLiveData.observe(this) { showUIData(it) }
        LiveDataHelper.appInfoLiveData.observe(this) { showUIData(it) }
        LiveDataHelper.changeDataLiveData.observe(this) { showUIData(it) }
    }

    private fun showUIData(it: String?) {
        binding.tvContent.text = it
    }

    private fun showResultUI(it: String?) {
        binding.tvRequestResult.text = "请求本地服务器响应的结果：\n$it"
    }

    private fun initView() {
        binding.tvServiceAddress.text = "本机服务器地址：$ipAddress"

        var serviceListData = ""
        ALSHelper.getServiceList().forEach { serviceListData += "服务名：${it.serviceName}   端口号：${it.port}\n" }
        binding.tvServiceList.text = serviceListData


        binding.btnQueryAppInfo.setOnClickListener {
            binding.tvContent.text = ""
            sendRequest("http://${ipAddress}:${androidServicePort}/appInfo")
        }
        binding.btnChangeData.setOnClickListener {
            sendRequest("http://${ipAddress}:${androidServicePort}/changeData?data=这是一条通过本地服务修改本地数据的请求${UUID.randomUUID()}")
        }
    }

    private fun sendRequest(url: String) {
        val request = Request.Builder().url(url).build();
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showResultUI(e.message)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()
                runOnUiThread {
                    showResultUI(result)
                }
            }
        })
    }

}