package org.obscurecore.developer

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Детальная информация об образовательном учреждении")
data class InstitutionDetails(
    @Schema(description = "Уникальный идентификатор учреждения", example = "123")
    val id: String,

    @Schema(description = "Тип учреждения, например, Школа или Детский сад", example = "Школа")
    val type: String,

    @Schema(description = "Номер учреждения", example = "№12")
    val number: String,

    @Schema(description = "Количество учащихся в учреждении", example = "350")
    val studentsCount: String,

    @Schema(description = "Район, в котором находится учреждение", example = "Московский")
    val district: String,

    @Schema(description = "Ссылка на страницу учреждения", example = "https://edu.tatar.ru/institution/123.htm")
    val url: String
)