# android local service 安卓本地微服务架构
本项目基于lygttpod扩展，增加了兼容文件上传multipart/form-data 和 application/json\xml

### [**Demo下载体验**](https://www.pgyer.com/MLpo)

0.0.6 支持请求header
0.0.7 支持自定义响应

1. 添加依赖

```groovy
    implementation 'io.github.mahongyin.android-local-service:core-lite:0.0.8' //不支持上传文件，够用
    implementation 'io.github.mahongyin.android-local-service:core:0.0.8'
    implementation 'io.github.mahongyin.android-local-service:annotation:0.0.8'
    kapt 'io.github.mahongyin.android-local-service:processor:0.0.8'
```

2. 创建本地服务（具体效果可以看项目demo） 定义如下类

```kotlin
   //@Service标记这是一个服务，端口号是服务器的端口号，注意端口号唯一
@Service(port = 2222)
abstract class AndroidService {

    //@Page标注页面类，打开指定h5页面
    @Page("index")
    fun getIndexFileName() = "test_page.html"

    //@Request注解在方法上边
    @Request("query")
    fun query(
        aaa: Boolean,
        bbb: Double,
        ccc: Float,
        ddd: String,
        eee: Int,
    ): List<String> {
        return listOf("$aaa", "$bbb", "$ccc", ddd, "$eee")
    }
    // @RequestHeader() 可以获取请求头参数，无注解则不关心header
    @Request("saveData")
    fun saveData(@RequestHeader() header: Map<String, String>, content: String) {
        LiveDataHelper.saveDataLiveData.postValue(content + UUID.randomUUID())
    }
    // 文件参数需要注解@UpFile
    @Request("saveFile")
    fun saveData(@UpFile file: File, content: String) {
        LiveDataHelper.saveDataLiveData.postValue(content + UUID.randomUUID())
    }   
    // json参数需要注解@UpJson
    @Request("saveJson")
    fun saveData(@UpJson json: String) {
        LiveDataHelper.saveDataLiveData.postValue(content + UUID.randomUUID())
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
```

3、初始化服务

```kotlin
        //①、初始化（建议在application中初始化）
        ALSHelper.init(this)
        
        //②、启动服务 
        //启动单个服务：
        ALSHelper.startService(ServiceConfig(AndroidService::class.java))

        //启动多个服务：
        ALSHelper.startServices(
            listOf(
                ServiceConfig(PCService::class.java),
                ServiceConfig(OtherService::class.java, 9527)
            )
        )
        
        //③、如需修改服务端口号可以在启动服务时候传入新的端口号
        //第一个参数是创建的服务类，第二个参数是 端口号，不传默认是AndroidService类中@Service注解中的端口号，这里优先级更高
        ServiceConfig(AndroidService::class.java, 9527)
```

4、局域网内浏览器通过一下方式即可看到效果

```
    192.168.31.157 是本机IP地址(每台设备都不一样)

    http://192.168.31.157:2222/index 
    http://192.168.31.157:2222/queryAppInfo 
    http://192.168.31.157:2222/saveData?content=我是浏览器发送的内容
```

5、demo是最好的老师，赶紧去体验一下demo吧！



----知识点：
在 JavaPoet 中，$N 和 $T 是两种不同的占位符，用于在生成代码时插入不同的元素：
$N (Name 占位符)
用于引用变量名、方法名、字段名等标识符
插入的是名称字符串，不包含类型信息
适用于已经声明的变量或方法名的引用
$T (Type 占位符)
用于引用类型（类、接口、基本类型等）
插入时会自动处理完整的类型信息，包括包名
会自动添加必要的 import 语句
适用于类型声明和类型引用
$S (String literals)的格式化。是一个占位符，用于字符串字面量
具体特点：
用途：用于在生成的 Java 代码中插入字符串常量
自动处理：会自动为字符串添加引号（""）
转义处理：会自动处理字符串中的特殊字符，如换行符、引号等
类型安全：确保生成的代码中字符串格式正确