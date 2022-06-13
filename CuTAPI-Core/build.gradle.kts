plugins {
    kotlin("jvm") version "1.6.10"

    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.31"
}

group = "xyz.mastriel"
version = "1.0"

repositories {
    mavenCentral()

    maven {
        name = "papermc-repo"
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        name = "codemc"
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
}

dependencies {
    implementation (kotlin("stdlib"))
    compileOnly    ("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")

    implementation ("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0")
    implementation ("org.jetbrains.kotlin:kotlin-reflect:1.6.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.3.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.1")
}

tasks.withType<ProcessResources> {
    val props = mapOf("version" to "$version")
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}