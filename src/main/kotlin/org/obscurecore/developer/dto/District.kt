package org.obscurecore.developer.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Перечисление доступных районов для фильтрации образовательных учреждений.
 */
@Schema(
    description = "Допустимые районы для фильтрации образовательных учреждений. " +
            "Доступные значения: AVIA (Авиастроительный), VAHI (Вахитовский), KIRO (Кировский), " +
            "MOSC (Московский), NOVO (Ново-Савиновский), PRIV (Приволжский), SOVI (Советский)"
)
enum class District(
    @Schema(description = "Название района", example = "Авиастроительный")
    val value: String
) {
    AVIA("Авиастроительный"),
    VAHI("Вахитовский"),
    KIRO("Кировский"),
    MOSC("Московский"),
    NOVO("Ново-Савиновский"),
    PRIV("Приволжский"),
    SOVI("Советский")
}