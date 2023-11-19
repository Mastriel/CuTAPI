import org.jetbrains.kotlin.konan.properties.loadProperties

group = "xyz.mastriel"
version = "0.0.1a"


plugins {
    kotlin("jvm") version "1.9.10"
    java

    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.31"
    id("xyz.jpenilla.run-paper") version "2.2.2"

    `maven-publish`
}

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
    maven {
        name = "lunari"
        url = uri("https://repo.lunari.studio/repository/maven-public/")
    }
}

