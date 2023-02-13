import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    java

    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.kotlin.plugin.serialization")

    `maven-publish`
}

val kotlinVersion : String by properties
group = "xyz.mastriel"
version = "0.0.2a"

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
    testImplementation("com.github.seeseemelk:MockBukkit-v1.19:2.119.3")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.19:2.119.3")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.19:2.119.3")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.19:2.119.3")
    testImplementation(kotlin("test"))
    implementation("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")
    implementation("com.comphenix.protocol:ProtocolLib:5.0.0-SNAPSHOT")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion}")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.4.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    compileOnly("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.6.0")
    compileOnly("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.6.0")
}


tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.shadow.get())
    archiveFileName.set("CuTAPI-v${archiveVersion.get()}.jar")
}


tasks.withType<ProcessResources> {
    val props = mapOf("version" to "$version", "kotlinVersion" to kotlinVersion)
    inputs.properties(props)
    filesMatching("plugin.yml") {
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
