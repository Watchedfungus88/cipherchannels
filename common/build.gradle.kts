plugins {
    `java-library`
}

sourceSets.test { resources.srcDir(rootProject.file("vectors")) }

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.gson)
    testImplementation(libs.gson)
    compileOnly("net.java.dev.jna:jna:5.14.0")
    testImplementation("net.java.dev.jna:jna:5.14.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    systemProperty("cipherchannels.test.nativeKeyring",
        providers.gradleProperty("nativeKeyringTest").getOrElse("false"))
}
