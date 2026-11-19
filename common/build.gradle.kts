// common: the pure-Java core (package ...core, no Minecraft classes) plus all shared Minecraft code.
// Loom is used here only to put Minecraft with official Mojang names on the compile classpath.
plugins {
    id("java-library")
    id("fabric-loom")
}

val minecraft_version: String by project
val fabric_loader_version: String by project

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())
    // Minecraft's own classes carry Fabric's @Environment markers; without the loader on the class path the
    // compiler warns about every one of them. Only needed to compile, nothing of it ends up in the mod.
    compileOnly("net.fabricmc:fabric-loader:$fabric_loader_version")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
