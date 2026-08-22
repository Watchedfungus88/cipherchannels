import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.Sync
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.SourceSetContainer
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.fabric.loom.remap) apply false
    alias(libs.plugins.neoforge.moddev) apply false
}

allprojects {
    group = "dev.cipherchannels"
    version = providers.gradleProperty("mod_version").get()

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

subprojects {
    pluginManager.withPlugin("java") {
        dependencies.add("testImplementation", dependencies.platform(libs.junit.bom))
        dependencies.add("testImplementation", libs.junit.jupiter)
        dependencies.add("testRuntimeOnly", libs.junit.launcher)
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
        if (project.name == "common") return@withPlugin

        val minecraftVersion = project.name.substringAfter('-')
        val javaVersion = if (minecraftVersion.startsWith("1.")) 21 else 25
        extensions.configure<BasePluginExtension> { archivesName = "cipherchannels" }
        extensions.configure<SourceSetContainer> {
            named("main") {
                java.srcDir(rootProject.file("minecraft/shared/src/main/java"))
                if (minecraftVersion != "1.21.1") java.srcDir(rootProject.file("minecraft/modern-shared/src/main/java"))
                if (minecraftVersion.startsWith("26.")) java.srcDir(rootProject.file("minecraft/extractor-shared/src/main/java"))
                java.srcDir(rootProject.file("minecraft/$minecraftVersion/src/main/java"))
            }
            named("test") {
                java.srcDir(rootProject.file("minecraft/shared/src/test/java"))
                if (minecraftVersion != "1.21.1") java.srcDir(rootProject.file("minecraft/modern-shared/src/test/java"))
                if (minecraftVersion.startsWith("26.")) java.srcDir(rootProject.file("minecraft/extractor-shared/src/test/java"))
                java.srcDir(rootProject.file("minecraft/$minecraftVersion/src/test/java"))
            }
        }
        dependencies.add("implementation", project(":common"))
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
            withSourcesJar()
        }
        tasks.withType<JavaCompile>().configureEach { options.release = javaVersion }
        tasks.named<Jar>("jar") {
            from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
            from(rootProject.file("LICENSE"))
            from(rootProject.file("THIRD_PARTY_NOTICES.md"))
        }
        if (project.name.startsWith("neoforge-")) tasks.named<Test>("test") { exclude("dev/cipherchannels/mixin/**") }
    }
    afterEvaluate {
        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.remove("-Xlint:all")
            if (!options.compilerArgs.contains("-Xlint:all,-classfile")) options.compilerArgs.add("-Xlint:all,-classfile")
        }
    }
}

tasks.register<Zip>("sourceReleaseZip") {
    archiveBaseName = "CipherChannels"
    archiveVersion = providers.gradleProperty("mod_version").get()
    archiveClassifier = "source"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(projectDir) { into("CipherChannels-${providers.gradleProperty("mod_version").get()}") }
    exclude { it.relativePath.segments.firstOrNull() == ".git" }
    exclude(".gradle/**", "**/.gradle/**", "**/build/**", "dist/**", "**/dist/**",
        "run/**", "**/run/**", "logs/**", "**/logs/**", ".idea/**",
        ".vscode/**", "*.iml", "**/.DS_Store", "*.launch", "*.launch.json")
    includeEmptyDirs = false
}

val targetProjects = subprojects.filter { it.name != "common" }
val assembleRelease by tasks.registering(Sync::class) {
    dependsOn(targetProjects.map { it.tasks.named("build") }, "sourceReleaseZip")
    into(layout.buildDirectory.dir("release"))
    targetProjects.forEach { target ->
        val artifact = "cipherchannels-${providers.gradleProperty("mod_version").get()}+${target.name.replace('-', '.')}.jar"
        from(target.layout.buildDirectory.dir("libs")) { include(artifact) }
    }
    from(tasks.named<Zip>("sourceReleaseZip").flatMap { it.archiveFile })
}

val writeSha256Sums by tasks.registering {
    dependsOn(assembleRelease)
    val releaseDirectory = layout.buildDirectory.dir("release")
    val output = releaseDirectory.map { it.file("SHA256SUMS") }
    inputs.dir(releaseDirectory)
    outputs.file(output)
    doLast {
        val directory = releaseDirectory.get().asFile
        val lines = directory.listFiles().orEmpty().filter { it.isFile && it.name != "SHA256SUMS" }
            .sortedBy { it.name }.map { file ->
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                    buffer.fill(0)
                }
                "${HexFormat.of().formatHex(digest.digest())}  ${file.name}"
            }
        output.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

val validateRelease by tasks.registering {
    dependsOn(writeSha256Sums)
    doLast {
        val directory = layout.buildDirectory.dir("release").get().asFile
        val jars = directory.listFiles { file -> file.extension == "jar" }.orEmpty().sortedBy { it.name }
        require(jars.size == targetProjects.size) { "Expected ${targetProjects.size} release JARs, found ${jars.size}" }
        for (jar in jars) {
            val target = jar.name.substringAfter('+').substringBeforeLast(".jar")
            val minecraft = target.substringAfter('.')
            ZipFile(jar).use { zip ->
                fun text(path: String): String {
                    val entry = requireNotNull(zip.getEntry(path)) { "${jar.name} is missing $path" }
                    return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                require(zip.getEntry("assets/cipherchannels/icon.png") != null)
                require(zip.getEntry("assets/cipherchannels/lang/en_us.json") != null)
                require(zip.getEntry("LICENSE") != null)
                require(zip.getEntry("THIRD_PARTY_NOTICES.md") != null)
                val mixins = text("cipherchannels.mixins.json")
                require(mixins.contains("\"required\": true"))
                require(mixins.contains("\"defaultRequire\": 1"))
                require(mixins.contains("CipherChannelsMixinPlugin"))
                for (mixin in listOf("ClientPacketListenerMixin", "ChatComponentMixin", "ChatScreenMixin", "ChatPatchesChatLogMixin")) {
                    require(mixins.contains(mixin)) { "${jar.name} is missing $mixin" }
                }
                if (target.startsWith("fabric.")) {
                    val metadata = text("fabric.mod.json")
                    require(metadata.contains("\"version\": \"${project.version}\""))
                    require(metadata.contains("\"environment\": \"client\""))
                    require(metadata.contains("\"minecraft\": \"=$minecraft\""))
                } else {
                    val metadata = text("META-INF/neoforge.mods.toml")
                    require(metadata.contains("version=\"${project.version}\""))
                    require(metadata.contains("versionRange=\"[$minecraft]\""))
                    require(metadata.contains("side=\"CLIENT\""))
                }
            }
        }
        val sums = directory.resolve("SHA256SUMS").readLines().filter { it.isNotBlank() }
        require(sums.size == jars.size + 1) { "SHA256SUMS must cover every JAR and the source ZIP" }
    }
}

tasks.register("releaseBuild") {
    dependsOn(validateRelease)
}
