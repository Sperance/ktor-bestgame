rootProject.name = "ktor-bestgame"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Общие правила игры (0.73.0): один код катает лут и считает лист на сервере и в клиенте.
includeBuild("rules")
