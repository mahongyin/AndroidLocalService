plugins {
    id("com.android.library")
}

android {
    //noinspection GradleDependency
    compileSdk = 29
    namespace = "com.android.local.service.core"
    defaultConfig {
        minSdk = 16
        targetSdk = 29
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

dependencies {
    api(project(mapOf("path" to ":annotation")))//加上它，使用端是不是不用再引入了
    api(files("libs/nanohttpd-2.3.5-SNAPSHOT.jar"))
//    api 'org.nanohttpd:nanohttpd:2.2.0'//-2.3.1 有bug 使用自编译jar
//    implementation 'com.google.code.gson:gson:2.8.6'
    //上传文件用的
    api("commons-fileupload:commons-fileupload:1.6.0")
//implementation 'commons-io:commons-io:2.20.0' // 上面包含了
    compileOnly("javax.servlet:javax.servlet-api:4.0.1")
}

apply(from = "../upload.gradle.kts")