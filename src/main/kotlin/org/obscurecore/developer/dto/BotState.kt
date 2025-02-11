package org.obscurecore.developer.dto

/**
 * Перечисление состояний диалога Telegram-бота.
 */
enum class BotState {
    IDLE,
    SELECT_SCRAPE_TYPE,
    SELECT_SCRAPE_DISTRICTS,
    WAITING_FILE,
    WAITING_PDF_FILE
}