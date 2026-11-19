// Root project: shared settings for all modules. The code lives in common, fabric and forge.
plugins {
    id("fabric-loom") version "1.11.8" apply false
}

subprojects {
    group = providers.gradleProperty("mod_group").get()
    version = providers.gradleProperty("mod_version").get()

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        // Minecraft 1.20.1 runs on Java 17
        options.release.set(17)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
}
