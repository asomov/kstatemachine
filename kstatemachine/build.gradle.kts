plugins {
    kotlin("jvm")
    `java-library`
    ru.nsk.`maven-publish`
    ru.nsk.jacoco
    id("org.jetbrains.dokka") version Versions.kotlin
}

group = rootProject.group
version = rootProject.version

tasks.test {
    useJUnitPlatform()
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = Versions.jvmTarget
            languageVersion = Versions.languageVersion
            apiVersion = Versions.apiVersion
        }
    }
}

dependencies {
    testImplementation("io.kotest:kotest-assertions-core:${Versions.kotestAssertions}")
    testImplementation("io.kotest:kotest-runner-junit5:${Versions.kotestRunner}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
}