import com.github.jengelman.gradle.plugins.shadow.tasks.*

plugins {
    kotlin("jvm") version "2.0.20"
    java

    id("com.gradleup.shadow") version "9.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.31"
    id("xyz.jpenilla.run-paper") version "2.3.1"

    `maven-publish`

    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    
    id("org.pkl-lang") version "0.30.2"
}

repositories {
    mavenCentral()
    gradlePluginPortal()

    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
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
version = properties["version"]!!
val minecraftVersion: String by properties




dependencies {
    testImplementation(kotlin("test"))

    // god hates me so we're shadowing everything
    shadow("org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion}")
    shadow("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    shadow("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.4.1")
    shadow("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    shadow("net.peanuuutz.tomlkt:tomlkt:0.3.7")

    shadow("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.16.0")
    shadow("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.16.0")
    shadow("net.kyori:adventure-extra-kotlin:4.17.0")

    // used for the built-in uploader
    shadow("io.ktor:ktor-server-core:2.3.12")
    shadow("io.ktor:ktor-server-netty:2.3.12")
    shadow("io.ktor:ktor-client-core:2.3.12")
    shadow("io.ktor:ktor-client-cio:2.3.12")

    shadow("net.lingala.zip4j:zip4j:2.11.5")
    shadow("com.jhlabs:filters:2.0.235-1")

    shadow("org.pkl-lang:pkl-config-kotlin:0.30.2")
    shadow("org.pkl-lang:pkl-codegen-kotlin:0.30.2")

    paperweight.paperDevBundle("${minecraftVersion}-R0.1-SNAPSHOT")
}


tasks {

    runServer {
        pluginJars("../CuTAPI/build/libs/CuTAPI-0.1.0a-reobf.jar")

        minecraftVersion(minecraftVersion)
    }
}

// use mojang mappings
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.shadow.get())
    archiveFileName.set("CuTAPI-v${archiveVersion.get()}.jar")

    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}


tasks.withType<ProcessResources> {
    val props = mapOf("version" to "$version", "kotlinVersion" to kotlinVersion)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

tasks.reobfJar {
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
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