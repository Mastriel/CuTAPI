@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    java

    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.kotlin.plugin.serialization")

    id("xyz.jpenilla.run-paper")

    `maven-publish`
}

val kotlinVersion : String by properties
group = "xyz.mastriel"
version = "0.1.0a"

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
    testImplementation(kotlin("test"))
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")

    // god hates me so we're shadowing everything
    shadow("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    shadow("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    shadow("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.4.1")
    shadow("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    shadow("net.peanuuutz.tomlkt:tomlkt:0.3.7")

    shadow("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.6.0")
    shadow("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.6.0")

}


tasks {

    runServer {
        downloadPlugins {
            url("https://github.com/dmulloy2/ProtocolLib/releases/download/5.1.0/ProtocolLib.jar")
        }

        minecraftVersion("1.20.1")
    }
}

tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.shadow.get())
    archiveFileName.set("CuTAPI-v${archiveVersion.get()}.jar")
}


tasks.withType<ProcessResources> {
    val props = mapOf("version" to "$version", "kotlinVersion" to kotlinVersion)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "xyz.mastriel"
            artifactId = "CuTAPI"
            version = version

            from(components["java"])
        }
    }
}