pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only for the QuickJS binding manga extensions expect the app to provide.
        exclusiveContent {
            forRepository { maven("https://www.jitpack.io") }
            filter { includeGroup("com.github.zhanghai.quickjs-java") }
        }
    }
}

rootProject.name = "Folio"
include(":app")
