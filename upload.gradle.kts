// 脚本插件(apply from)中不允许使用 plugins {} 块，只能用命令式方式应用插件
// 插件版本已由根 build.gradle.kts 的 plugins { ... apply false } 提供
apply(plugin = "com.vanniktech.maven.publish")
//apply(plugin = "signing") // add

//allprojects {
//    plugins.withId("com.vanniktech.maven.publish") {
//        mavenPublishing {
//            sonatypeHost = "S01"
//        }
//    }
//}
//
//publishing {
//    repositories {
//        maven {
//            isAllowInsecureProtocol = true
//            val uploadLocal = findProperty("UPLOAD_LOCAL")?.toString() ?: "false"
//            if (uploadLocal.toBoolean()) {
//                val homeDir = System.getProperty("user.home")
//                url = uri("file://$homeDir/maven/repos/")
//            } else {
//                url = uri(
//                    if (version.toString().endsWith("SNAPSHOT")) mavenSnapshotsUrl else mavenReleasesUrl
//                )
//                credentials {
////                    val localProperties = Properties()
////                    localProperties.load(FileInputStream(rootProject.file("local.properties")))
////                    username = localProperties.getProperty("mavenCentralUsername")
////                    password = localProperties.getProperty("mavenCentralPassword")
//                    username = sonatypeUsername
//                    password = sonatypePassword
//                }
//            }
//        }
//    }
//}

//  获取项目名
val projectName = rootProject.name  //分享git是取的项目名
val projectDirName = rootProject.projectDir.name// 项目文件夹名

val gitRepo = "github.com/mahongyin"
val versionName = property("VERSION_NAME").toString()
val groupName = "io.github.mahongyin.$projectDirName" //自行分辨 用项目名还是目录名

//  获取当前模块名
val moduleName = project.name

// 配置坐标 (参一为 GroupId, 参二为模块名, 参三为版本号 "1.0.0")
// vanniktech 扩展的 groupId/version 默认取自 project.group/project.version，artifactId 默认取自 project.name
group = groupName
version = versionName

// 脚本插件的编译 classpath 不继承工程的插件，因此拿不到 mavenPublishing 类型安全访问器，
// 也无法直接引用 vanniktech 的扩展类型（会导致类型不一致），
// 改用 Gradle 自带的 PublishingExtension 配置 POM，效果等价
configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        artifactId = moduleName
        pom {
            // 模块名
            name.set(moduleName)
            description.set("A description of what ${projectName} does.")
            inceptionYear.set("2025")
            url.set("https://${gitRepo}/${projectName}/")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
//                  name.set("LGPL-3.0 license")
//                  url.set("https://www.gnu.org/licenses/lgpl-3.0.html")
//                  distribution.set("https://www.gnu.org/licenses/lgpl-3.0.html")
                }
            }
            developers {
                developer {
                    id.set(gitRepo.replace("github.com/", ""))
                    name.set(gitRepo.replace("github.com/", ""))
                    url.set("https://${gitRepo}/")
                    //email.set("")
                }
            }
            scm {
                url.set("https://${gitRepo}/${projectName}/")
                connection.set("scm:git:git://${gitRepo}/${projectName}.git")
                developerConnection.set("scm:git:ssh://git@${gitRepo}/${projectName}.git")
            }
        }
    }
}
// 若改用 vanniktech 扩展（mavenPublishing {}）直接配置，需把本文件放到 buildSrc 做成预编译脚本插件，
// 或在各模块的 plugins {} 中声明 com.vanniktech.maven.publish 后于模块脚本内配置：
//    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL) // add
//    // signing 进行签名
//    signAllPublications() // add

//../gradlew publishToMavenCentral
// 自动发布
//../gradlew publishAndReleaseToMavenCentral
// https://repo1.maven.org/maven2/io/github/1976222027/
tasks.register<Exec>("uploadMavenCentral") {
    group = "publishing"
    description = "Uploads all archives to the remote Maven repository."
    workingDir = rootProject.projectDir
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("cmd", "/c", "gradlew.bat", "publishToMavenCentral")
    } else {
        commandLine("./gradlew", "publishToMavenCentral")
    }
    standardOutput = System.out
    errorOutput = System.err
    isIgnoreExitValue = true
}

tasks.register<Exec>("uploadAutoMavenCentral") {
    group = "publishing"
    description = "Uploads all archives to the remote Maven repository."
    workingDir = rootProject.projectDir
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("cmd", "/c", "gradlew.bat", "publishAndReleaseToMavenCentral")
    } else {
        commandLine("./gradlew", "publishAndReleaseToMavenCentral")
    }
    standardOutput = System.out
    errorOutput = System.err
    isIgnoreExitValue = true
}
