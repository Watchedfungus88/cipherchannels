pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CipherChannels"

include("common", "fabric-1.21.1", "fabric-1.21.11", "fabric-26.1", "fabric-26.1.2", "fabric-26.2",
    "neoforge-1.21.1", "neoforge-1.21.11", "neoforge-26.1", "neoforge-26.1.2", "neoforge-26.2")

project(":fabric-1.21.1").projectDir = file("platforms/fabric-1.21.1")
project(":fabric-1.21.11").projectDir = file("platforms/fabric-1.21.11")
project(":fabric-26.1").projectDir = file("platforms/fabric-26.1")
project(":fabric-26.1.2").projectDir = file("platforms/fabric-26.1.2")
project(":fabric-26.2").projectDir = file("platforms/fabric-26.2")
project(":neoforge-1.21.1").projectDir = file("platforms/neoforge-1.21.1")
project(":neoforge-1.21.11").projectDir = file("platforms/neoforge-1.21.11")
project(":neoforge-26.1").projectDir = file("platforms/neoforge-26.1")
project(":neoforge-26.1.2").projectDir = file("platforms/neoforge-26.1.2")
project(":neoforge-26.2").projectDir = file("platforms/neoforge-26.2")
