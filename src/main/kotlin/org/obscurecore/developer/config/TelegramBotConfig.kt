package org.obscurecore.developer.config

import org.obscurecore.developer.service.MyTelegramBot
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

/**
 * Конфигурация для регистрации Telegram-бота.
 */
@Configuration
class TelegramBotConfig {

    @Bean
    fun telegramBotsApi(myTelegramBot: MyTelegramBot): TelegramBotsApi {
        return TelegramBotsApi(DefaultBotSession::class.java).apply {
            registerBot(myTelegramBot)
        }
    }
}