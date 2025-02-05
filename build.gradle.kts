import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.1.2"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
}

group = "org.obscurecore.developer"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {

    // Основная зависимость Starter для Spring Boot (часто хватает одной):




    // Включает Web + логирование (Logback) по умолчанию
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Если добавляли раньше, можете убрать:
    // implementation("org.springframework.boot:spring-boot-starter")

    // Исключаем log4j-slf4j2-impl из телеграм-стартер
    implementation("org.telegram:telegrambots-spring-boot-starter:6.5.0") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
    }

    // Jsoup для веб-скрапинга
    implementation("org.jsoup:jsoup:1.16.1")

    // Apache POI для работы с Excel файлами
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Jackson для JSON и Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenCSV для работы с CSV файлами
    implementation("com.opencsv:opencsv:5.7.1")

    // Springdoc OpenAPI для генерации документации Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0")

    // Kotlin Reflection
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Kotlin Standard Library
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Тестирование
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}