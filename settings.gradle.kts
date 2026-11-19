pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.minecraftforge.net/") { name = "Forge" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // lets Gradle download a matching JDK (Java 17 for the Forge module) if none is installed
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "Athanorium"

// common: simulation core (pure Java) and all shared Minecraft code
// fabric / forge: only the loader specific glue
include("common")
include("fabric")
if (providers.gradleProperty("forge_enabled").getOrElse("true").toBoolean()) {
    include("forge")
}
