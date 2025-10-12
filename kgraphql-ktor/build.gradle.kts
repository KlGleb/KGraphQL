plugins {
    base
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "1.8.0"
}

val caffeine_version: String by project
val kDataLoader_version: String by project
val kotlin_version: String by project
val serialization_version: String by project
val coroutine_version: String by project
val jackson_version: String by project
val ktor_version: String by project

val netty_version: String by project
val hamcrest_version: String by project
val kluent_version: String by project
val junit_version: String by project

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")


dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":kgraphql"))
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-serialization:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit_version")
    testImplementation("org.amshove.kluent:kluent:$kluent_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.ktor:ktor-server-auth:$ktor_version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit_version")
    implementation("io.ktor:ktor-server-sse:3.3.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:${jackson_version}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jackson_version}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
//    compileKotlin { kotlinOptions { jvmTarget = JavaVersion.VERSION_11.toString() } }
//    compileTestKotlin { kotlinOptions { jvmTarget = JavaVersion.VERSION_11.toString() } }

    test {
        useJUnitPlatform()
    }

}

