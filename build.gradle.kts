import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.fabric.loom)
}

group = providers.gradleProperty("maven_group").get()
version = providers.gradleProperty("mod_version").get() + "+mc" + libs.versions.minecraft.get()

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven { url = uri("https://api.modrinth.com/maven") } }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.key.mapping)
    implementation(libs.fabric.lifecycle)
    compileOnly(libs.modmenu)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks {
    processResources {
        val values = mapOf(
            "version" to project.version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "java_version" to libs.versions.java.get(),
            "loader_version" to libs.versions.fabric.loader.get(),
        )
        inputs.properties(values)
        filesMatching("fabric.mod.json") { expand(values) }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = libs.versions.java.get().toInt()
        options.compilerArgs.add("-Xlint:all")
    }

    test {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
    }

    jar {
        archiveClassifier.set("")
        from("LICENSE")
        from("THIRD_PARTY_NOTICES.md")
    }

    register<Zip>("sourceReleaseZip") {
        group = "distribution"
        description = "Packages a clean, portable source release."
        archiveBaseName = "CipherChannels"
        archiveVersion = project.version.toString()
        archiveClassifier = "source"
        destinationDirectory = layout.buildDirectory.dir("distributions")
        from(projectDir) {
            into("CipherChannels-${project.version}")
        }
        exclude { it.relativePath.segments.firstOrNull() == ".git" }
        exclude(".gradle/**", "build/**", "dist/**", "run/**", "logs/**",
            ".idea/**", ".vscode/**", "*.iml", "**/.DS_Store", "*.launch", "*.launch.json")
        includeEmptyDirs = false
    }

    register("verifyRelease") {
        group = "verification"
        dependsOn("jar", "sourceReleaseZip", "test")
        doLast {
            val artifact = layout.buildDirectory.file("libs/cipherchannels-${project.version}.jar").get().asFile
            check(artifact.isFile && artifact.length() > 0L) { "Missing mod JAR" }
            ZipFile(artifact).use { jar ->
                val metadata = jar.getEntry("fabric.mod.json")
                check(metadata != null) { "Mod JAR is missing fabric.mod.json" }
                val text = jar.getInputStream(metadata).reader(Charsets.UTF_8).readText()
                check(text.contains("\"id\": \"cipherchannels\"")) { "Unexpected mod metadata" }
                check(jar.getEntry("assets/cipherchannels/icon.png") != null) { "Mod JAR is missing its icon" }
                check(jar.getEntry("assets/cipherchannels/lang/en_us.json") != null) { "Mod JAR is missing translations" }
                check(jar.getEntry("THIRD_PARTY_NOTICES.md") != null) { "Mod JAR is missing third-party notices" }
            }
            val sourceArchive = layout.buildDirectory.file(
                "distributions/CipherChannels-${project.version}-source.zip").get().asFile
            check(sourceArchive.isFile && sourceArchive.length() > 0L) { "Missing source release" }
            ZipFile(sourceArchive).use { zip ->
                val forbidden = zip.entries().asSequence().map { it.name }.firstOrNull { name ->
                    name.contains("/.git/") || name.contains("/.gradle/") || name.contains("/build/") || name.contains("/dist/")
                        || name.contains("/run/") || name.contains("/logs/") || name.endsWith("/.DS_Store")
                }
                check(forbidden == null) { "Source release contains forbidden entry: $forbidden" }
            }
        }
    }

    build { dependsOn("verifyRelease") }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    withSourcesJar()
}
