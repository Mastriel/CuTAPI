group = "xyz.mastriel"
version = "1.0"

val kotlinVersion : String by properties

plugins {
    kotlin("jvm") version "1.7.20-RC"
    java

    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.5.31"

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

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
