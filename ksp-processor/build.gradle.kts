plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // 必须是 implementation（而不是 compileOnly）：
    // 处理器在 KSP 运行时需要加载注解类来取全限定名，用 compileOnly 会导致 NoClassDefFoundError。
    // 这些类不会被打进处理器 jar，只是出现在处理器的运行期 classpath 上。
    implementation(project(":annotation"))
    implementation("com.squareup:javapoet:1.13.0")
    // KSP 处理器 API，版本与根 build.gradle.kts 中的 KSP 插件保持一致
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.0-1.0.29")
}

apply(from = "../upload.gradle.kts")
