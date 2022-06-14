val kodeinDbVersion: String by project

plugins {
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
    id("net.ormr.kommando.plugin") version "0.2.0"
}

group = "net.ormr.humbaba"
version = "0.2.0"

kommando {
    version = "0.0.16"
    processor {
        autoSearch = true
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:3.4.2")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.commons:commons-text:1.9")
    implementation("net.ormr.krautils:krautils-core:0.2.0")
    implementation("io.github.reactivecircus.cache4k:cache4k:0.5.0")

    implementation("org.kodein.db:kodein-db-jvm:$kodeinDbVersion")
    implementation("org.kodein.db:kodein-db-serializer-kotlinx:$kodeinDbVersion")
    implementation("org.kodein.db:kodein-leveldb-jni-jvm:$kodeinDbVersion")

    implementation("ch.qos.logback:logback-classic:1.3.0-alpha12")
    implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger:1.0.4")

    implementation("net.ormr.katbox:katbox:0.3.0")
    implementation("net.ormr.kixiv:kixiv:0.2.0")

    implementation("io.ktor:ktor-client-resources:2.0.0-beta-1")

    implementation("net.peanuuutz:tomlkt:0.1.0")

    testImplementation(kotlin("test"))
}

tasks {
    test {
        useJUnitPlatform()
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xcontext-receivers",
                "-opt-in=kotlin.contracts.ExperimentalContracts",
            )
        }
    }
}

application {
    mainClass.set("net.ormr.humbaba.MainKt")
}