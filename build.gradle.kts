plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.dokka)
}

group = "ru.descend"
version = "0.9.0"

application {
    mainClass = "ru.descend.bootstrap.ApplicationKt"
}

kotlin {
    jvmToolchain(21)
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
    }
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
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.kotlinx.datetime)

    implementation(libs.opensavvy.ktmongo)
    implementation(libs.swagger.annotations)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.mongo.bson)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
}
// Pinned data is verified at build time and packaged in the JAR. No runtime downloads.
val preparePoeCatalog by tasks.registering(Exec::class) {
    inputs.files("scripts/prepare_poe.py", "data/poe.lock.json")
    inputs.dir("data/poe/compact")
    outputs.dir(layout.buildDirectory.dir("generated-poe"))
    commandLine("python3", "scripts/prepare_poe.py")
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated-poe")) }
tasks.processResources { dependsOn(preparePoeCatalog) }

tasks.register<Test>("poeTest") {
    description = "Deterministic PoE domain tests; no external MongoDB required"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    maxHeapSize = "2g"
    filter { includeTestsMatching("features.poe.*"); excludeTestsMatching("features.poe.PoeMongoTest") }
}


tasks.register<Test>("poeMongoTest") {
    description = "PoE transactions against a dedicated MongoDB replica set"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    maxHeapSize = "2g"
    filter { includeTestsMatching("features.poe.PoeMongoTest") }
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// The legacy manual MongoTest modifies shared fixtures; run it explicitly against a disposable DB.
// The dedicated transactional suite has its own guarded task and replica set.
tasks.test {
    exclude("ru/descend/MongoTest.class", "features/poe/PoeMongoTest.class")
    maxHeapSize = "2g"
}

tasks.register<Test>("legacyMongoTest") {
    description = "Opt-in legacy database fixtures; use only a disposable database"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("ru.descend.MongoTest") }
    doFirst { require(System.getenv("MONGO_DB") == "legacy_integration_test") }
}
