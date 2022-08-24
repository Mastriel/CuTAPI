import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")

    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.kotlin.plugin.serialization")
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
        name = "protocol-lib"
        url = uri("https://repo.dmulloy2.net/repository/public/")
    }
}


dependencies {
    testImplementation("com.github.seeseemelk:MockBukkit-v1.19:2.106.0")
    testImplementation(kotlin("test"))
    compileOnly("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.0")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.7.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.3.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.0.0-SNAPSHOT")
    // controversial choice, i know
    shadow("de.tr7zw:item-nbt-api:2.10.0")
}


tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.shadow.get())

    relocate("de.tr7zw.changeme.nbtapi", "cutapi.shadow.nbtapi")
}


tasks.withType<ProcessResources> {
    val props = mapOf("version" to "$version")
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}