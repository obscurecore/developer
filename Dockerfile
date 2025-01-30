# Этап сборки: используем образ Gradle с JDK 17 для сборки приложения
FROM gradle:7.6.1-jdk17 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle bootJar --no-daemon

# Этап выполнения: используем образ с JDK 17 для запуска приложения
FROM openjdk:17
VOLUME /tmp
VOLUME /data  
ARG JAR_FILE=build/libs/*.jar
COPY --from=build /home/gradle/project/${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]