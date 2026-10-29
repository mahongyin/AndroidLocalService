import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    compileSdk = 31
    namespace = "com.android.local.service"
    defaultConfig {
        applicationId = "com.android.local.service"
        minSdk = 21
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            // 在打包时移除项目中的相关文件，不打入apk文件中
            excludes += "META-INF/core_release.kotlin_module"
//            // 当有多个匹配项的时候匹配到第一个就可以了
//            pickFirsts += listOf("filepath")
//            // 编译打包时，找不到工具链的。由于NDK开发工具的包的问题导致如果不需要对应的架构可以直接设置
//            jniLibs.keepDebugSymbols += listOf("filepath")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.appcompat:appcompat:1.3.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.9")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

//    implementation(project(":annotation"))
//    implementation(project(":core"))
    implementation(project(":core-lite"))
    // 注解处理器二选一（不要同时引入，否则会生成重复的 ALS_XXX 类）：
    //kapt(project(":processor"))
    ksp(project(":ksp-processor"))

//    implementation("io.github.mahongyin.android-local-service:annotation:${VERSION_NAME}")
//    implementation("io.github.mahongyin.android-local-service:core:${VERSION_NAME}")
//    implementation("io.github.mahongyin.android-local-service:core-lite:${VERSION_NAME}")
//    kapt("io.github.mahongyin.android-local-service:processor:${VERSION_NAME}")
//    ksp("io.github.mahongyin.android-local-service:ksp-processor:${VERSION_NAME}")
}
