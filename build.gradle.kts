// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        //classpath 'com.vanniktech:gradle-maven-publish-plugin:0.34.0'
    }
}

plugins {
    id("com.android.application") version "8.12.0" apply false
    id("com.android.library") version "8.12.0" apply false
    // Kotlin 版本必须和 KSP 的版本前缀保持一致（KSP 2.1.0-1.0.29 -> Kotlin 2.1.0）
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
