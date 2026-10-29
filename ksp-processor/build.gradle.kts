plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
//    implementation(project(":annotation"))
    implementation("com.squareup:javapoet:1.13.0")
    // KSP 处理器 API，版本与根 build.gradle.kts 中的 KSP 插件保持一致
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.0-1.0.29")
}

apply(from = "../upload.gradle.kts")
