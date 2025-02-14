package org.obscurecore.developer.service

import java.io.ByteArrayInputStream
import java.net.URI
import org.obscurecore.developer.dto.BotState
import org.obscurecore.developer.dto.ScrapeSettings
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

@Component
class MyTelegramBot(
    @Value("\${telegram.bot.token}") private val botToken: String,
    @Value("\${telegram.bot.username}") private val botUsername: String,
    @Value("\${api.base-url}") private val apiBaseUrl: String
) : TelegramLongPollingBot() {

    private val logger = LoggerFactory.getLogger(MyTelegramBot::class.java)
    private val restTemplate = RestTemplate()
    private val MAX_MESSAGE_LENGTH = 4000

    // Хранение состояний пользователей
    private val userStates = mutableMapOf<Long, BotState>()
    private val userScrapeSettings = mutableMapOf<Long, ScrapeSettings>()

    override fun getBotToken() = botToken
    override fun getBotUsername() = botUsername

    override fun onUpdateReceived(update: Update?) {
        if (update == null) return

        if (update.hasMessage()) {
            val message = update.message
            val chatId = message.chatId
            val text = message.text ?: ""

            // Если пользователь заходит впервые, сразу показываем главное меню с приветствием
            if (!userStates.containsKey(chatId)) {
                userStates[chatId] = BotState.IDLE
                userScrapeSettings[chatId] = ScrapeSettings()
                showMainMenu(chatId, "Привет! Добро пожаловать. Выберите действие:")
                return
            }

            // Если пользователь отправил команду /menu или нажал кнопку "🏠 Главное меню" – возвращаем в главное меню
            if (text.trim().equals("/menu", ignoreCase = true) || text.trim() == "🏠 Главное меню") {
                userStates[chatId] = BotState.IDLE
                userScrapeSettings[chatId] = ScrapeSettings()
                showMainMenu(chatId, "Главное меню: выберите действие")
                return
            }

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
                    else -> { }
                }
            }

            // Обработка текстовых сообщений в зависимости от состояния диалога
            when (userStates.getOrDefault(chatId, BotState.IDLE)) {
                BotState.IDLE -> {
                    when (text) {
                        "📊 Скрапить учреждения" -> {
                            userStates[chatId] = BotState.SELECT_SCRAPE_TYPE
                            sendMessageWithKeyboard(chatId, "Выберите формат результата:", buildScrapeTypeKeyboard())
                        }
                        "📥 Загрузить Excel LandPlot" -> {
                            userStates[chatId] = BotState.WAITING_FILE
                            sendMessageWithKeyboard(chatId, "Пришлите Excel-файл (.xlsx)", buildBackToMenuKeyboard())
                        }
                        "🖼 Извлечь PDF изображения" -> {
                            userStates[chatId] = BotState.WAITING_PDF_FILE
                            sendMessageWithKeyboard(chatId, "Пришлите PDF-документ (.pdf)", buildBackToMenuKeyboard())
                        }
                        else -> {
                            sendLongMessage(chatId.toString(), "Неизвестная команда. Для возврата в меню нажмите /menu.")
                        }
                    }
                }
                BotState.SELECT_SCRAPE_TYPE -> {
                    when (text) {
                        "Текст" -> {
                            userScrapeSettings.getOrPut(chatId) { ScrapeSettings() }.excel = false
                            userStates[chatId] = BotState.SELECT_SCRAPE_DISTRICTS
                            sendMessageWithKeyboard(chatId, "Выберите район(ы) для скрапинга:", buildDistrictKeyboard(chatId))
                        }
                        "Excel" -> {
                            userScrapeSettings.getOrPut(chatId) { ScrapeSettings() }.excel = true
                            userStates[chatId] = BotState.SELECT_SCRAPE_DISTRICTS
                            sendMessageWithKeyboard(chatId, "Выберите район(ы) для скрапинга:", buildDistrictKeyboard(chatId))
                        }
                        else -> {
                            sendLongMessage(chatId.toString(), "Неизвестная команда. Выберите один из вариантов.")
                        }
                    }
                }
                BotState.SELECT_SCRAPE_DISTRICTS -> {
                    when (text) {
                        "Готово" -> {
                            performScrape(chatId)
                        }
                        else -> {
                            // Обработка выбора района; если нажата кнопка с галочкой – убираем её, иначе добавляем
                            val district = text.replace("✔️ ", "")
                            toggleDistrictSelection(chatId, district)
                            sendMessageWithKeyboard(chatId, "Выберите район(ы) для скрапинга (отмеченные добавлены):", buildDistrictKeyboard(chatId))
                        }
                    }
                }
                else -> {
                    sendLongMessage(chatId.toString(), "Неизвестная команда. Для возврата в меню нажмите /menu.")
                }
            }
        }
    }

    /**
     * Отправка сообщения с ReplyKeyboardMarkup.
     */
    private fun sendMessageWithKeyboard(chatId: Long, text: String, keyboard: ReplyKeyboardMarkup) {
        val msg = SendMessage().apply {
            this.chatId = chatId.toString()
            this.text = text
            this.replyMarkup = keyboard
        }
        try {
            execute(msg)
        } catch (e: TelegramApiException) {
            logger.error("Ошибка отправки сообщения с клавиатурой: ${e.message}", e)
        }
    }

    /**
     * Показывает главное меню с кнопками.
     */
    private fun showMainMenu(chatId: Long, text: String) {
        sendMessageWithKeyboard(chatId, text, buildMainMenuKeyboard())
    }

    /**
     * Главное меню с кнопками, оформленными в стиле "Material View".
     */
    private fun buildMainMenuKeyboard(): ReplyKeyboardMarkup {
        val keyboard = ReplyKeyboardMarkup()
        val row1 = KeyboardRow().apply { add("📊 Скрапить учреждения") }
        val row2 = KeyboardRow().apply { add("📥 Загрузить Excel LandPlot") }
        val row3 = KeyboardRow().apply { add("🖼 Извлечь PDF изображения") }
        keyboard.keyboard = listOf(row1, row2, row3)
        keyboard.resizeKeyboard = true
        keyboard.oneTimeKeyboard = false
        return keyboard
    }

    /**
     * Клавиатура для выбора типа скрапинга.
     */
    private fun buildScrapeTypeKeyboard(): ReplyKeyboardMarkup {
        val keyboard = ReplyKeyboardMarkup()
        val row1 = KeyboardRow().apply {
            add("Текст")
            add("Excel")
        }
        val row2 = KeyboardRow().apply { add("🏠 Главное меню") }
        keyboard.keyboard = listOf(row1, row2)
        keyboard.resizeKeyboard = true
        keyboard.oneTimeKeyboard = false
        return keyboard
    }

    /**
     * Клавиатура для выбора районов.
     * Отмечает выбранные районы галочкой.
     */
    private fun buildDistrictKeyboard(chatId: Long): ReplyKeyboardMarkup {
        val keyboard = ReplyKeyboardMarkup()
        val settings = userScrapeSettings.getOrPut(chatId) { ScrapeSettings() }
        val available = listOf("AVIA", "VAHI", "KIRO", "MOSC", "NOVO", "PRIV", "SOVI")
        val rows = mutableListOf<KeyboardRow>()
        available.chunked(3).forEach { chunk ->
            val row = KeyboardRow()
            chunk.forEach { district ->
                val isSelected = settings.districts.contains(district)
                val buttonText = if (isSelected) "✔️ $district" else district
                row.add(buttonText)
            }
            rows.add(row)
        }
        val lastRow = KeyboardRow().apply {
            add("Готово")
            add("🏠 Главное меню")
        }
        rows.add(lastRow)
        keyboard.keyboard = rows
        keyboard.resizeKeyboard = true
        keyboard.oneTimeKeyboard = false
        return keyboard
    }

    /**
     * Клавиатура с единственной кнопкой "🏠 Главное меню".
     */
    private fun buildBackToMenuKeyboard(): ReplyKeyboardMarkup {
        val keyboard = ReplyKeyboardMarkup()
        val row = KeyboardRow().apply { add("🏠 Главное меню") }
        keyboard.keyboard = listOf(row)
        keyboard.resizeKeyboard = true
        keyboard.oneTimeKeyboard = false
        return keyboard
    }

    /**
     * Переключает выбор района для скрапинга.
     */
    private fun toggleDistrictSelection(chatId: Long, district: String) {
        val settings = userScrapeSettings.getOrPut(chatId) { ScrapeSettings() }
        if (settings.districts.contains(district)) settings.districts.remove(district)
        else settings.districts.add(district)
    }

    /**
     * Запускает скрапинг с выбранными настройками.
     */
    private fun performScrape(chatId: Long) {
        val settings = userScrapeSettings[chatId] ?: ScrapeSettings()
        val urlBuilder = StringBuilder("$apiBaseUrl/scrape?update=true")
        if (settings.excel) urlBuilder.append("&excel=true")
        if (settings.districts.isNotEmpty()) urlBuilder.append("&districts=${settings.districts.joinToString(",")}")
        val finalUrl = urlBuilder.toString()

        sendMessageWithKeyboard(chatId, "Запуск скрапинга...", buildBackToMenuKeyboard())

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
        val headers = HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
        }
        val body = LinkedMultiValueMap<String, Any>()
        val fileResource = object : org.springframework.core.io.ByteArrayResource(fileBytes) {
            override fun getFilename() = originalFilename
        }
        body.add("file", fileResource)
        val requestEntity = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(url, requestEntity, String::class.java)
        return if (response.statusCode.is2xxSuccessful && !response.body.isNullOrEmpty()) response.body!!
        else "Не удалось загрузить данные. Код ответа: ${response.statusCodeValue}"
    }

    private fun uploadPdfToServer(fileBytes: ByteArray, originalFilename: String): ByteArray {
        val url = "$apiBaseUrl/extractPdfImages"
        val headers = HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
        }
        val body = LinkedMultiValueMap<String, Any>()
        val fileResource = object : org.springframework.core.io.ByteArrayResource(fileBytes) {
            override fun getFilename() = originalFilename
        }
        body.add("file", fileResource)
        val requestEntity = HttpEntity(body, headers)
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
}