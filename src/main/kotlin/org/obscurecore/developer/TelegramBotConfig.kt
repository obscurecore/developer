package org.obscurecore.developer

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@Configuration
class TelegramBotConfig {

    @Bean
    fun telegramBotsApi(myTelegramBot: MyTelegramBot): TelegramBotsApi {
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
        botsApi.registerBot(myTelegramBot)
        return botsApi
    }
}