import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.1.2" // Убедитесь, что версия актуальна
    id("io.spring.dependency-management") version "1.1.0" // Актуальная версия
    kotlin("jvm") version "1.9.25" // Актуальная версия Kotlin
    kotlin("plugin.spring") version "1.9.25"
}

group = "org.obscurecore.developer"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17 // Или ваша версия Java

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starter Web включает в себя spring-boot-starter-logging (SLF4J и Logback)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.telegram:telegrambots-spring-boot-starter:6.5.0") // проверьте актуальную версию

    // Jsoup для веб-скрапинга
    implementation("org.jsoup:jsoup:1.16.1")

    // Apache POI для работы с Excel файлами
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Jackson для JSON и Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenCSV для работы с CSV файлами
    implementation("com.opencsv:opencsv:5.7.1")


    // Springdoc OpenAPI для генерации документации Swagger (обновлённая зависимость для Spring Boot 3)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0")

    // Kotlin Reflection
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Kotlin Standard Library
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Тестирование
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Исключение конфликтующих зависимостей
configurations.all {
    exclude(group = "commons-logging", module = "commons-logging")
    exclude(group = "org.apache.logging.log4j", module = "log4j-api")
    exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
    exclude(group = "org.slf4j", module = "jul-to-slf4j")
}

// Настройки компилятора Kotlin
tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17" // Совместимо с Java версией, указанной выше
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}