pluginManagement {
    repositories {
//        maven { url 'https://maven.aliyun.com/repository/google' }
//        maven { url 'https://maven.aliyun.com/repository/public' }
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://maven.aliyun.com/repository/google")
        // central仓和jcenter仓的聚合仓
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")

        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }

}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
//        maven { url 'https://maven.aliyun.com/repository/google' }
//        maven { url 'https://maven.aliyun.com/repository/public' }
        google()
        mavenCentral()
        maven(url = "https://maven.aliyun.com/repository/google")
        // central仓和jcenter仓的聚合仓
        maven(url = "https://maven.aliyun.com/repository/public")
    }
}
rootProject.name = "AndroidLocalService"
include(":app")
include(":annotation")
include(":ksp-processor")
include(":core")
include(":core-lite")
