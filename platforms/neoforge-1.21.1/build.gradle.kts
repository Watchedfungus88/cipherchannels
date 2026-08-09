import org.gradle.api.tasks.SourceSetContainer

val commonMain = project(":common").extensions.getByType<SourceSetContainer>()["main"]

plugins {
    id("net.neoforged.moddev")
}

version = "1.0.0+neoforge.1.21.1"

base {
    archivesName = "cipherchannels"
}

sourceSets {
    main { java.srcDir(rootProject.file("minecraft/1.21.1/src/main/java")) }
    test { java.srcDir(rootProject.file("minecraft/1.21.1/src/test/java")) }
}

neoForge {
    version = "21.1.248"
    mods {
        create("cipherchannels") {
            sourceSet(sourceSets.main.get())
            sourceSet(commonMain)
        }
    }
    unitTest {
        enable()
        testedMod = mods.getByName("cipherchannels")
    }
}

dependencies {
    implementation(project(":common"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.processResources {
    val values = mapOf("version" to "1.0.0", "minecraft_version" to "1.21.1",
        "neoforge_version" to "21.1.248")
    inputs.properties(values)
    filesMatching("META-INF/neoforge.mods.toml") { expand(values) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    useJUnitPlatform()
    exclude("dev/cipherchannels/mixin/**")
}

tasks.jar {
    from(commonMain.output)
    from(rootProject.file("LICENSE"))
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}
