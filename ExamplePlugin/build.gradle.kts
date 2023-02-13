import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar


plugins {
    kotlin("jvm")

    id("com.github.johnrengelman.shadow")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val kotlinVersion : String by properties
group = "xyz.mastriel"
version = "0.0.1a"

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
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    testImplementation("com.github.seeseemelk:MockBukkit-v1.19:2.117.1")
    testImplementation(kotlin("test"))
    implementation(projects.cuTAPICore)
    implementation("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.4.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    compileOnly("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:2.6.0")
    compileOnly("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:2.6.0")

    compileOnly("org.mongodb:mongodb-driver-sync:4.7.2")
    shadow("org.litote.kmongo:kmongo-coroutine-serialization:4.7.2") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }

    shadow("com.github.Trigary:AdvancementCreator:v2.0")
}


tasks.withType<ShadowJar> {
    configurations = listOf(project.configurations.shadow.get())
}
