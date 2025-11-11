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
    }
}

toolchainManagement {
    jvm {
        jdkDownload {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        }
    }
}

rootProject.name = "Agent_App"
include(":app")
include(":backend")
 