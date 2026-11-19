// fabric: loader glue for Fabric. The sources and resources of common are compiled into this module.
plugins {
    id("fabric-loom")
}

evaluationDependsOn(":common")

val minecraft_version: String by project
val fabric_loader_version: String by project
val fabric_api_version: String by project
val mod_id: String by project

base {
    archivesName.set("$mod_id-fabric-$minecraft_version")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_version")
    compileOnly(project(":common", "namedElements"))
}

val commonMain = project(":common").extensions.getByType<SourceSetContainer>()["main"]

tasks.compileJava {
    source(commonMain.allJava)
}

tasks.processResources {
    from(commonMain.resources)
    val props = mapOf(
        "mod_id" to mod_id,
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.version,
        "mod_description" to project.property("mod_description"),
        "mod_license" to project.property("mod_license"),
        "minecraft_version" to minecraft_version,
        "fabric_loader_version" to fabric_loader_version
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

loom {
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("run")
        }
        named("server") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("run/server")
        }
    }
}

