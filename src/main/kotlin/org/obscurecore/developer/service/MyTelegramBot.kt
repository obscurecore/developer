package org.obscurecore.developer.service

import java.io.ByteArrayInputStream
import java.net.URI
import org.obscurecore.developer.dto.BotState
import org.obscurecore.developer.dto.ScrapeSettings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

/**
 * Telegram-бот для управления скрапингом, загрузкой Excel и извлечением изображений из PDF.
 */
@Component
class MyTelegramBot(
    @Value("\${telegram.bot.token}") private val botToken: String,
    @Value("\${telegram.bot.username}") private val botUsername: String,
    @Value("\${api.base-url}") private val apiBaseUrl: String
) : TelegramLongPollingBot() {

    private val logger = LoggerFactory.getLogger(MyTelegramBot::class.java)
    private val restTemplate = RestTemplate()
    private val MAX_MESSAGE_LENGTH = 4000

    // Управление состоянием пользователей
    private val userStates = mutableMapOf<Long, BotState>()
    private val userScrapeSettings = mutableMapOf<Long, ScrapeSettings>()

    override fun getBotToken() = botToken
    override fun getBotUsername() = botUsername

    override fun onUpdateReceived(update: Update?) {
        if (update == null) return

        if (update.hasCallbackQuery()) {
            handleCallback(update.callbackQuery)
            return
        }

        if (update.hasMessage()) {
            val message = update.message
            val chatId = message.chatId
            val text = message.text ?: ""

            // Обработка документов (Excel или PDF)
            if (message.hasDocument()) {
                when (userStates[chatId]) {
                    BotState.WAITING_FILE -> {
                        handleExcelFileUpload(message.document, chatId)
                        return
                    }

                    BotState.WAITING_PDF_FILE -> {
                        handlePdfFileUpload(message.document, chatId)
                        return
                    }

                    else -> {}
                }
            }

            when {
                text.startsWith("/start") -> {
                    userStates[chatId] = BotState.IDLE
                    userScrapeSettings[chatId] = ScrapeSettings()
                    showMainMenu(chatId, "Привет! Я бот для компании «Авторы». Выберите действие:")
                }

                else -> {
                    sendLongMessage(chatId.toString(), "Неизвестная команда. Для начала работы нажмите /start.")
                }
            }
        }
    }

    private fun handleCallback(callbackQuery: org.telegram.telegrambots.meta.api.objects.CallbackQuery) {
        val chatId = callbackQuery.message.chatId
        val messageId = callbackQuery.message.messageId
        val data = callbackQuery.data

        when (data) {
            "DO_SCRAPE" -> {
                userStates[chatId] = BotState.SELECT_SCRAPE_TYPE
                editTextAndKeyboard(chatId, messageId, "Выберите формат результата:", buildScrapeTypeButtons())
            }

            "DO_UPLOAD" -> {
                userStates[chatId] = BotState.WAITING_FILE
                editTextAndKeyboard(chatId, messageId, "Пришлите Excel-файл (.xlsx)", null)
            }

            "DO_EXTRACT_PDF" -> {
                userStates[chatId] = BotState.WAITING_PDF_FILE
                editTextAndKeyboard(chatId, messageId, "Пришлите PDF-документ (.pdf)", null)
            }

            "SCRAPE_TEXT" -> {
                userScrapeSettings.getOrPut(chatId) { ScrapeSettings() }.excel = false
                userStates[chatId] = BotState.SELECT_SCRAPE_DISTRICTS
                editTextAndKeyboard(chatId, messageId, "Выберите район(ы) для скрапинга:", buildDistrictButtons())
            }

            "SCRAPE_EXCEL" -> {
                userScrapeSettings.getOrPut(chatId) { ScrapeSettings() }.excel = true
                userStates[chatId] = BotState.SELECT_SCRAPE_DISTRICTS
                editTextAndKeyboard(chatId, messageId, "Выберите район(ы) для скрапинга:", buildDistrictButtons())
            }

            "SCRAPE_DISTRICT_DONE" -> performScrape(chatId, messageId)
            else -> {
                if (data.startsWith("district_")) {
                    val districtName = data.substringAfter("district_")
                    toggleDistrictSelection(chatId, districtName)
                    editTextAndKeyboard(
                        chatId,
                        messageId,
                        "Выберите район(ы) для скрапинга (отмеченные добавлены):",
                        buildDistrictButtons()
                    )
                }
            }
        }
    }

    private fun showMainMenu(chatId: Long, text: String) {
        val buttons = InlineKeyboardMarkup.builder().keyboard(
            listOf(
                listOf(
                    InlineKeyboardButton.builder().text("📊 Скрапить учреждения").callbackData("DO_SCRAPE").build()
                ),
                listOf(
                    InlineKeyboardButton.builder().text("📥 Загрузить Excel LandPlot").callbackData("DO_UPLOAD").build()
                ),
                listOf(
                    InlineKeyboardButton.builder().text("🖼 Извлечь PDF изображения").callbackData("DO_EXTRACT_PDF")
                        .build()
                )
            )
        ).build()

        val msg = SendMessage().apply {
            this.chatId = chatId.toString()
            this.text = text
            this.replyMarkup = buttons
        }
        try {
            execute(msg)
        } catch (e: TelegramApiException) {
            logger.error("Ошибка отправки сообщения: ${e.message}", e)
        }
    }

    private fun buildScrapeTypeButtons(): InlineKeyboardMarkup {
        val textBtn = InlineKeyboardButton.builder().text("Текст").callbackData("SCRAPE_TEXT").build()
        val excelBtn = InlineKeyboardButton.builder().text("Excel").callbackData("SCRAPE_EXCEL").build()
        return InlineKeyboardMarkup.builder().keyboard(listOf(listOf(textBtn, excelBtn))).build()
    }

    private fun buildDistrictButtons(): InlineKeyboardMarkup {
        val available = listOf("AVIA", "VAHI", "KIRO", "MOSC", "NOVO", "PRIV", "SOVI")
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        val rowSize = 3
        available.chunked(rowSize).forEach { chunk ->
            val row = chunk.map { district ->
                val isSelected =
                    userScrapeSettings.getOrPut(district.hashCode().toLong()) { ScrapeSettings() }.districts.contains(
                        district
                    )
                val text = if (isSelected) "✔️ $district" else district
                InlineKeyboardButton.builder().text(text).callbackData("district_$district").build()
            }
            rows.add(row)
        }
        val doneBtn = InlineKeyboardButton.builder().text("Готово").callbackData("SCRAPE_DISTRICT_DONE").build()
        rows.add(listOf(doneBtn))
        return InlineKeyboardMarkup.builder().keyboard(rows).build()
    }

    private fun toggleDistrictSelection(chatId: Long, district: String) {
        val settings = userScrapeSettings.getOrPut(chatId) { ScrapeSettings() }
        if (settings.districts.contains(district)) settings.districts.remove(district)
        else settings.districts.add(district)
    }

    private fun performScrape(chatId: Long, messageId: Int) {
        val settings = userScrapeSettings[chatId] ?: ScrapeSettings()
        val urlBuilder = StringBuilder("$apiBaseUrl/scrape?update=true")
        if (settings.excel) urlBuilder.append("&excel=true")
        if (settings.districts.isNotEmpty()) urlBuilder.append("&districts=${settings.districts.joinToString(",")}")
        val finalUrl = urlBuilder.toString()
        editTextAndKeyboard(chatId, messageId, "Запуск скрапинга...", null)

        try {
            if (settings.excel) {
                val responseEntity = restTemplate.getForEntity(URI(finalUrl), ByteArray::class.java)
                val fileBytes = responseEntity.body
                if (responseEntity.statusCode.is2xxSuccessful && fileBytes != null && fileBytes.isNotEmpty()) {
                    sendDocument(chatId, fileBytes, "institutions.xlsx", "Результаты в формате Excel")
                } else {
                    sendLongMessage(chatId.toString(), "Пустой ответ или не удалось получить файл.")
                }
            } else {
                val response = restTemplate.getForObject(finalUrl, String::class.java)
                if (response.isNullOrEmpty()) {
                    sendLongMessage(chatId.toString(), "Пустой ответ от сервера.")
                } else {
                    sendLongMessage(chatId.toString(), response)
                }
            }
        } catch (ex: Exception) {
            sendLongMessage(chatId.toString(), "Ошибка при скрапинге: ${ex.message}")
        } finally {
            userStates[chatId] = BotState.IDLE
            userScrapeSettings[chatId] = ScrapeSettings()
        }
    }

    private fun handleExcelFileUpload(document: org.telegram.telegrambots.meta.api.objects.Document, chatId: Long) {
        try {
            val filePath = downloadFilePath(document.fileId)
            val fileBytes = downloadFileAsBytes(filePath)
            val resultText = uploadExcelToServer(fileBytes, document.fileName ?: "uploaded.xlsx")
            sendLongMessage(chatId.toString(), resultText)
        } catch (ex: Exception) {
            sendLongMessage(chatId.toString(), "Ошибка при загрузке файла: ${ex.message}")
        } finally {
            userStates[chatId] = BotState.IDLE
        }
    }

    private fun handlePdfFileUpload(document: org.telegram.telegrambots.meta.api.objects.Document, chatId: Long) {
        try {
            val filePath = downloadFilePath(document.fileId)
            val fileBytes = downloadFileAsBytes(filePath)
            val resultBytes = uploadPdfToServer(fileBytes, document.fileName ?: "uploaded.pdf")
            if (resultBytes.isNotEmpty()) {
                sendDocument(chatId, resultBytes, "extracted_images.zip", "Извлеченные изображения")
            } else {
                sendLongMessage(chatId.toString(), "Пустой ответ от сервера при извлечении изображений.")
            }
        } catch (ex: Exception) {
            sendLongMessage(chatId.toString(), "Ошибка при загрузке PDF: ${ex.message}")
        } finally {
            userStates[chatId] = BotState.IDLE
        }
    }

    private fun uploadExcelToServer(fileBytes: ByteArray, originalFilename: String): String {
        val url = "$apiBaseUrl/uploadLandplots?text=true"
        val headers = org.springframework.http.HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
        }
        val body = org.springframework.util.LinkedMultiValueMap<String, Any>()
        val fileResource = object : org.springframework.core.io.ByteArrayResource(fileBytes) {
            override fun getFilename() = originalFilename
        }
        body.add("file", fileResource)
        val requestEntity = org.springframework.http.HttpEntity(body, headers)
        val response = restTemplate.postForEntity(url, requestEntity, String::class.java)
        return if (response.statusCode.is2xxSuccessful && !response.body.isNullOrEmpty()) response.body!!
        else "Не удалось загрузить данные. Код ответа: ${response.statusCodeValue}"
    }

    private fun uploadPdfToServer(fileBytes: ByteArray, originalFilename: String): ByteArray {
        val url = "$apiBaseUrl/extractPdfImages"
        val headers = org.springframework.http.HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
        }
        val body = org.springframework.util.LinkedMultiValueMap<String, Any>()
        val fileResource = object : org.springframework.core.io.ByteArrayResource(fileBytes) {
            override fun getFilename() = originalFilename
        }
        body.add("file", fileResource)
        val requestEntity = org.springframework.http.HttpEntity(body, headers)
        val response = restTemplate.postForEntity(url, requestEntity, ByteArray::class.java)
        return response.body ?: ByteArray(0)
    }

    private fun sendDocument(chatId: Long, fileBytes: ByteArray, fileName: String, caption: String) {
        val sendDoc = SendDocument().apply {
            this.chatId = chatId.toString()
            this.document = InputFile(ByteArrayInputStream(fileBytes), fileName)
            this.caption = caption
        }
        try {
            execute(sendDoc)
        } catch (e: TelegramApiException) {
            logger.error("Ошибка при отправке документа: ${e.message}", e)
            sendLongMessage(chatId.toString(), "Ошибка при отправке документа: ${e.message}")
        }
    }

    private fun downloadFilePath(fileId: String): String {
        val file = execute(org.telegram.telegrambots.meta.api.methods.GetFile(fileId))
        return file.filePath
    }

    private fun downloadFileAsBytes(filePath: String): ByteArray {
        return downloadFileAsStream(filePath).readBytes()
    }

    private fun sendLongMessage(chatId: String, text: String) {
        text.chunked(MAX_MESSAGE_LENGTH).forEach { part ->
            val msg = SendMessage(chatId, part)
            try {
                execute(msg)
            } catch (e: TelegramApiException) {
                logger.error("Ошибка отправки длинного сообщения: ${e.message}", e)
            }
        }
    }

    private fun editTextAndKeyboard(chatId: Long, messageId: Int, newText: String, keyboard: InlineKeyboardMarkup?) {
        val editMessage = EditMessageText().apply {
            this.chatId = chatId.toString()
            this.messageId = messageId
            this.text = newText
            this.replyMarkup = keyboard
        }
        try {
            execute(editMessage)
        } catch (e: TelegramApiException) {
            logger.error("Ошибка редактирования сообщения: ${e.message}", e)
        }
    }
}