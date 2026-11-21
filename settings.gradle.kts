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
        // Only for QuickJS: the extension sandbox's JS engine isn't on Maven Central.
        exclusiveContent {
            forRepository { maven("https://www.jitpack.io") }
            filter { includeGroup("com.github.zhanghai.quickjs-java") }
        }
    }
}

rootProject.name = "Folio"
include(":app")
