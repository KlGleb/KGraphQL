import de.marcphilipp.gradle.nexus.NexusPublishPlugin
import java.time.Duration

val version: String by project
val sonatypeUsername: String? = System.getenv("sonatypeUsername")
val sonatypePassword: String? = System.getenv("sonatypePassword")

plugins {
    id("com.github.ben-manes.versions") version "0.44.0"
    id("de.marcphilipp.nexus-publish") version "0.4.0"
    jacoco
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = "com.apurebase"
    version = version
}


tasks {
}
