package org.obscurecore.developer.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Конфигурация OpenAPI для генерации Swagger-документации.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI = OpenAPI().info(
        Info()
            .title("Land Plot API")
            .version("1.0")
            .description("API для загрузки и обработки данных земельных участков")
    )
}