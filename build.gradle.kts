plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "ru.descend"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

    val exposed = "1.1.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed")
    implementation("org.jetbrains.exposed:exposed-json:$exposed")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed")

    implementation("com.zaxxer:HikariCP:7.0.2")

    implementation("org.postgresql:postgresql:42.7.6")
}
