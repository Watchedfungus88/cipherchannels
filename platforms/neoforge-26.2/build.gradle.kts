import org.gradle.api.tasks.SourceSetContainer

plugins { id("net.neoforged.moddev") }

val minecraftVersion = name.substringAfter('-')
val neoForgeVersion = libs.versions.neoforge.mc262.get()
val commonMain = project(":common").extensions.getByType<SourceSetContainer>()["main"]
version = "${rootProject.version}+neoforge.$minecraftVersion"

neoForge {
    version = neoForgeVersion
    mods { create("cipherchannels") { sourceSet(sourceSets.main.get()); sourceSet(commonMain) } }
    unitTest { enable(); testedMod = mods.getByName("cipherchannels") }
}

tasks.processResources {
    val values = mapOf("version" to rootProject.version.toString(), "minecraft_version" to minecraftVersion,
        "neoforge_version" to neoForgeVersion)
    inputs.properties(values)
    filesMatching("META-INF/neoforge.mods.toml") { expand(values) }
}
