plugins {
    kotlin("jvm") version "2.1.21"
    application
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
}

group = "com.adhyatmic"
version = "1.0.0-engine"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("io.ktor:ktor-server-core:3.1.2")
    implementation("io.ktor:ktor-server-netty:3.1.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("io.ktor:ktor-server-status-pages:3.1.2")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("javax.inject:javax.inject:1")
}

application {
    mainClass.set("com.adhyatmic.vedicengine.MainKt")
}

// Self-contained freeze — no vendor/vedic-mitra checkout required.
val frozenRoot = rootProject.projectDir.resolve("third_party/vedic-mitra")

sourceSets {
    main {
        kotlin {
            srcDir(frozenRoot.resolve("astronomy-kotlin"))
            srcDir(frozenRoot.resolve("common-kotlin"))
        }
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
