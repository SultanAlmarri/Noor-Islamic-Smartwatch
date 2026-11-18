pluginManagement {
    repositories {
        google()
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

rootProject.name = "NoorIslamicSmartwatch"
include(":app")
include(":wear")
include(":core:prayer")
include(":alerts")
include(":shared")
include(":face")
// Testing only: a widget host app that shows the phone app's widgets as a launcher does (tools/devicetest/widgets.sh).
include(":widgethost")
project(":widgethost").projectDir = file("tools/widgethost")
