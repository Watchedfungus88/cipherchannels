plugins {
    id("net.fabricmc.fabric-loom")
}

version = "1.0.0+fabric.26.1.2"

base {
    archivesName = "cipherchannels"
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

sourceSets {
    main { java.srcDir(rootProject.file("minecraft/26.1.2/src/main/java")) }
    test { java.srcDir(rootProject.file("minecraft/26.1.2/src/test/java")) }
}

loom {
    mods {
        create("cipherchannels") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":common"))
    minecraft("com.mojang:minecraft:26.1.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.155.2+26.1.2")
    compileOnly("maven.modrinth:modmenu:18.0.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.processResources {
    val values = mapOf("version" to "1.0.0", "minecraft_version" to "26.1.2",
        "java_version" to "25", "loader_version" to "0.19.3")
    inputs.properties(values)
    filesMatching("fabric.mod.json") { expand(values) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].output)
    from(rootProject.file("LICENSE"))
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}
