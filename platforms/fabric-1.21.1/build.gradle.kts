plugins { id("net.fabricmc.fabric-loom-remap") }

val minecraftVersion = name.substringAfter('-')
version = "${rootProject.version}+fabric.$minecraftVersion"

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

loom { mods { create("cipherchannels") { sourceSet(sourceSets.main.get()) } } }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    add("modImplementation", libs.fabric.loader)
    add("modImplementation", libs.fabric.api.mc1211)
    add("modCompileOnly", libs.modmenu.mc1211)
}

tasks.processResources {
    val values = mapOf("version" to rootProject.version.toString(), "minecraft_version" to minecraftVersion,
        "java_version" to "21", "loader_version" to libs.versions.fabric.loader.get())
    inputs.properties(values)
    filesMatching("fabric.mod.json") { expand(values) }
}
