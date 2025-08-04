plugins {
    base
//    alias(libs.plugins.kotlin.jvm)
    kotlin("jvm") version "2.1.0"
}

val serialization_version: String by project
val coroutine_version: String by project
//val jackson_version: String by project

val netty_version: String by project
val hamcrest_version: String by project
val kluent_version: String by project
val junit_version: String by project

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")


dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.serialization)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.jackson.core)
    implementation(libs.jackson.module)
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.sse)

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit_version")
    testImplementation("org.amshove.kluent:kluent:$kluent_version")
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.auth)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit_version")
    implementation(project(":KGraphQL:kgraphql"))
}


tasks {
//    compileKotlin { kotlinOptions { jvmTarget = JavaVersion.VERSION_11.toString() } }
//    compileTestKotlin { kotlinOptions { jvmTarget = JavaVersion.VERSION_11.toString() } }

    test {
        useJUnitPlatform()
    }
}