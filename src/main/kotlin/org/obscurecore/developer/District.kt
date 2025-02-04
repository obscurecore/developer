package org.obscurecore.developer

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Допустимые районы для фильтрации образовательных учреждений")
enum class District(@Schema(description = "Название района", example = "Авиастроительный") val value: String) {
    AVIA("Авиастроительный"),
    VAHI("Вахитовский"),
    KIRO("Кировский"),
    MOSC("Московский"),
    NOVO("Ново-Савиновский"),
    PRIV("Приволжский"),
    SOVI("Советский")
}