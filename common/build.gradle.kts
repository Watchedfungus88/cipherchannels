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
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.add("-Xlint:all")
}
