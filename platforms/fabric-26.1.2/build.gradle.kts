plugins { id("net.fabricmc.fabric-loom") }

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
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api.mc2612)
    compileOnly(libs.modmenu.mc2612)
}

tasks.processResources {
    val values = mapOf("version" to rootProject.version.toString(), "minecraft_version" to minecraftVersion,
        "java_version" to "25", "loader_version" to libs.versions.fabric.loader.get())
    inputs.properties(values)
    filesMatching("fabric.mod.json") { expand(values) }
}
