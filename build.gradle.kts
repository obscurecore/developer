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
    // Spring Boot Starter для web-приложения (включает встроенное логирование)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Telegram-бот (исключаем конфликтующий логгер)
    implementation("org.telegram:telegrambots-spring-boot-starter:6.5.0") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
    }

    // Jsoup для веб-скрапинга
    implementation("org.jsoup:jsoup:1.16.1")

    // Apache POI для работы с Excel (XLSX)
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Jackson для работы с JSON и Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenCSV для обработки CSV файлов
    implementation("com.opencsv:opencsv:5.7.1")

    // Springdoc OpenAPI для генерации Swagger документации
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0")

    // Зависимости Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Apache PDFBox для извлечения изображений из PDF
    implementation("org.apache.pdfbox:pdfbox:2.0.28")

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