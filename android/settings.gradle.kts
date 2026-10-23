pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tolly"

include(":app")
include(":core:model")
include(":core:auth")
include(":core:network")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":feature:login")
include(":feature:courses")
include(":feature:announcements")
include(":feature:agenda")
include(":feature:downloads")
include(":feature:settings")
