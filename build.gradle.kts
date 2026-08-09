import org.gradle.api.tasks.bundling.Zip

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.18" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.17.18" apply false
    id("net.neoforged.moddev") version "2.0.143" apply false
}

allprojects {
    group = "dev.cipherchannels"
    version = "1.0.0"
}

tasks.register<Zip>("sourceReleaseZip") {
    archiveBaseName = "CipherChannels"
    archiveVersion = "1.0.0"
    archiveClassifier = "source"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(projectDir) { into("CipherChannels-1.0.0") }
    exclude { it.relativePath.segments.firstOrNull() == ".git" }
    exclude(".gradle/**", "**/.gradle/**", "**/build/**", "dist/**", "**/dist/**",
        "run/**", "**/run/**", "logs/**", "**/logs/**", ".idea/**",
        ".vscode/**", "*.iml", "**/.DS_Store", "*.launch", "*.launch.json")
    includeEmptyDirs = false
}

tasks.register("releaseBuild") {
    dependsOn(subprojects.map { it.tasks.named("build") }, "sourceReleaseZip")
}
