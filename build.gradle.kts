import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.9.22"
    java

    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.31"
    id("xyz.jpenilla.run-paper") version "2.2.2"

    `maven-publish`

    id("io.papermc.paperweight.userdev") version "1.5.11"

}

repositories {
    mavenCentral()
    gradlePluginPortal()

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
val kotlinVersion: String by properties
group = "xyz.mastriel"
version = "0.1.0a"




dependencies {
    testImplementation(kotlin("test"))
    // compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")


    // god hates me so we're shadowing everything
    shadow("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    shadow("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    shadow("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.4.1")
    shadow("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    shadow("net.peanuuutz.tomlkt:tomlkt:0.3.7")

    shadow("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.14.0")
    shadow("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.14.0")

    // used for the built-in uploader
    shadow("io.ktor:ktor-server-core:2.3.0")
    shadow("io.ktor:ktor-server-netty:2.3.0")

    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
}


tasks {

    runServer {
        downloadPlugins {
            url("https://ci.dmulloy2.net/job/ProtocolLib/lastBuild/artifact/build/libs/ProtocolLib.jar")
        }

        minecraftVersion("1.20.4")
    }
}

tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.shadow.get())
    // archiveFileName.set("CuTAPI-v${archiveVersion.get()}.jar")
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


tasks.assemble {
    dependsOn(tasks.reobfJar)

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