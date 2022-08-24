group = "xyz.mastriel"
version = "1.0"

plugins {
    kotlin("jvm") version "1.7.0"

    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.31"
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
