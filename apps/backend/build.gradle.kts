plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.wnl"
version = "0.0.1-SNAPSHOT"
description = "cash-chat-api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0")
    }
}

dependencies {

    // implementation
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter")

    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // test's implementation
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    testImplementation("org.springframework.security:spring-security-test")

    // Kotest
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:mysql:1.20.4")

    // WebClient (SSE 소비용)
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")

    // Mockito-Kotlin
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

    // run-time only
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")

    // test's run-time only
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
