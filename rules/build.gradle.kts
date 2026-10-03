import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    `java-library`
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.sperance.exileforge"
version = "1.0.0"

// Общий код сервера и Android-клиента: только стандартная библиотека и kotlinx.serialization,
// байткод Java 17 - ниже потолка обеих сторон.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ktlint {
    version.set("1.8.0")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

// Симуляция экономики (отчёт, не тест): свой набор исходников поверх main - в jar правил и в check не входит,
// новых зависимостей не требует. Запуск: ./gradlew simulateEconomy [-Pcontent=<папка content>] [-Pseeds=<N>]
val sim: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}

tasks.register<JavaExec>("simulateEconomy") {
    group = "reporting"
    description = "Economy simulation: build/reports/economy/economy.md and economy.csv"
    classpath = sim.runtimeClasspath
    mainClass.set("com.sperance.exileforge.rules.sim.EconomySimKt")
    val content = providers.gradleProperty("content").orElse(layout.projectDirectory.dir("../src/main/resources/content").asFile.path)
    val out = layout.buildDirectory.dir("reports/economy")
    systemProperty("content", content.get())
    systemProperty("seeds", providers.gradleProperty("seeds").getOrElse("20"))
    args(out.get().asFile.path)
    outputs.upToDateWhen { false }
}
