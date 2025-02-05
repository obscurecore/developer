package org.obscurecore.developer

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

@Component
class MyTelegramBot(
    @Value("\${telegram.bot.token}") private val botToken: String,
    @Value("\${telegram.bot.username}") private val botUsername: String,
    @Value("\${api.base-url}") private val apiBaseUrl: String
) : TelegramLongPollingBot() {

    private val restTemplate = RestTemplate()

    // Ограничение на длину сообщения в символах
    private val MAX_MESSAGE_LENGTH = 4000

    override fun getBotToken() = botToken

    override fun getBotUsername() = botUsername

    override fun onUpdateReceived(update: Update?) {
        if (update?.hasMessage() == true && update.message.hasText()) {
            val messageText = update.message.text.trim()
            val chatId = update.message.chatId.toString()
            val responseText = when {
                messageText.startsWith("/scrape") -> {
                    try {
                        val url = "$apiBaseUrl/scrape?update=true"
                        val response = restTemplate.getForObject(url, String::class.java)
                        "Скрапинг выполнен. Ответ API: $response"
                    } catch (ex: Exception) {
                        "Ошибка при вызове API: ${ex.message}"
                    }
                }
                messageText.startsWith("/upload") -> {
                    "Команда /upload пока не поддерживается."
                }
                else -> "Неизвестная команда. Доступны команды: /scrape, /upload"
            }
            sendLongMessage(chatId, responseText)
        }
    }

    private fun sendLongMessage(chatId: String, text: String) {
        // Разбиваем текст на части, если он превышает MAX_MESSAGE_LENGTH
        val parts = text.chunked(MAX_MESSAGE_LENGTH)
        parts.forEach { part ->
            val message = SendMessage().apply {
                this.chatId = chatId
                this.text = part
            }
            try {
                execute(message)
            } catch (e: TelegramApiException) {
                e.printStackTrace()
            }
        }
    }
}