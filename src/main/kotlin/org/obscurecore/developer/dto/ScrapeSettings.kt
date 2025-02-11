package org.obscurecore.developer.dto

/**
 * Модель настроек скрапинга для Telegram-бота.
 */
data class ScrapeSettings(
    var excel: Boolean = false,
    var districts: MutableSet<String> = mutableSetOf()
)