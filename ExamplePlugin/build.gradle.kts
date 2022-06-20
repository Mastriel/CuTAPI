import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.7.0"

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
    maven {
        name = "lunari"
        url = uri("https://repo.lunari.studio/repository/maven-public/")
    }
}

dependencies {
    compileOnly(projects.cuTAPICore)
    compileOnly("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.0")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.7.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.3.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.2")

    // controversial choice, i know
    compileOnly("de.tr7zw:item-nbt-api:2.10.0")
}


tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.shadow.get())

    relocate("de.tr7zw.changeme.nbtapi", "cutapi.shadow.nbtapi")
}
