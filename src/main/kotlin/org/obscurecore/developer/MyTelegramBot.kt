package org.obscurecore.developer

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.*
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.ByteArrayInputStream
import java.net.URI

/**
 * Состояние диалога для каждого пользователя
 */
enum class BotState {
    IDLE,
    SELECT_SCRAPE_TYPE,         // Выбор: вывести текст или Excel
    SELECT_SCRAPE_DISTRICTS,    // Выбор районов
    WAITING_FILE                // Ожидание файла после команды "Загрузить"
}

/**
 * Модель "настроек" для скрапинга, которые пользователь постепенно выбирает
 */
data class ScrapeSettings(
    var excel: Boolean = false,
    var districts: MutableSet<String> = mutableSetOf()
)

/**
 * Основной бот с пошаговой логикой и inline-кнопками.
 */
@Component
class MyTelegramBot(
    @Value("\${telegram.bot.token}") private val botToken: String,
    @Value("\${telegram.bot.username}") private val botUsername: String,
    @Value("\${api.base-url}") private val apiBaseUrl: String
) : TelegramLongPollingBot() {

    private val restTemplate = RestTemplate()
    private val MAX_MESSAGE_LENGTH = 4000

    // Храним состояние для каждого пользователя
    private val userStates = mutableMapOf<Long, BotState>()

    // Временное хранение настроек для скрапинга
    private val userScrapeSettings = mutableMapOf<Long, ScrapeSettings>()

    override fun getBotToken() = botToken
    override fun getBotUsername() = botUsername

    override fun onUpdateReceived(update: Update?) {
        // Проверяем, что за апдейт
        if (update == null) return

        // Сначала обрабатываем Callback Query (нажатие inline-кнопок)
        if (update.hasCallbackQuery()) {
            handleCallback(update.callbackQuery)
            return
        }

        // Если простое сообщение (текст / файл)
        if (update.hasMessage()) {
            val message = update.message
            val chatId = message.chatId
            val text = message.text ?: ""

            // Если пользователь прислал документ, проверяем, не ждём ли мы файл
            if (message.hasDocument()) {
                if (userStates[chatId] == BotState.WAITING_FILE) {
                    // Обрабатываем загрузку
                    handleFileUpload(message.document, chatId)
                }
                return
            }

            // Обработка команд
            when {
                text.startsWith("/start") -> {
                    userStates[chatId] = BotState.IDLE
                    userScrapeSettings[chatId] = ScrapeSettings()
                    showMainMenu(chatId, "Привет! Я бот для компании «Авторы».\nВыберите действие:")
                }
                else -> {
                    sendLongMessage(
                        chatId.toString(),
                        "Неизвестная команда.\nДля начала работы нажмите /start."
                    )
                }
            }
        }
    }

    /**
     * Обработка нажатий inline-кнопок (callback_data).
     */
    private fun handleCallback(callbackQuery: CallbackQuery) {
        val chatId = callbackQuery.message.chatId
        val messageId = callbackQuery.message.messageId
        val data = callbackQuery.data // Строка callback_data из кнопки

        // Смотрим текущее состояние
        val state = userStates[chatId] ?: BotState.IDLE

        // Чтобы обновить сообщение (с кнопками), можно использовать EditMessageText
        when (data) {
            "DO_SCRAPE" -> {
                // Пользователь выбрал "Скрапить"
                userStates[chatId] = BotState.SELECT_SCRAPE_TYPE
                editTextAndKeyboard(chatId, messageId, "Выберите формат результата:", buildScrapeTypeButtons())
            }
            "DO_UPLOAD" -> {
                // Пользователь выбрал "Загрузить файл"
                userStates[chatId] = BotState.WAITING_FILE
                // Обновим сообщение, уберём кнопки
                editTextAndKeyboard(chatId, messageId, "Хорошо, пришлите Excel-файл (.xlsx)", null)
            }
            "SCRAPE_TEXT" -> {
                // Выбрали результат в виде текста
                userScrapeSettings[chatId]?.excel = false
                userStates[chatId] = BotState.SELECT_SCRAPE_DISTRICTS
                editTextAndKeyboard(chatId, messageId, "Выберите район(ы) для скрапинга:", buildDistrictButtons())
            }
            "SCRAPE_EXCEL" -> {
                // Выбрали результат Excel
                userScrapeSettings[chatId]?.excel = true
                userStates[chatId] = BotState.SELECT_SCRAPE_DISTRICTS
                editTextAndKeyboard(chatId, messageId, "Выберите район(ы) для скрапинга:", buildDistrictButtons())
            }
            "SCRAPE_DISTRICT_DONE" -> {
                // Завершаем выбор районов, делаем запрос
                performScrape(chatId, messageId)
            }
            else -> {
                // Возможно, это district_XXX
                if (data.startsWith("district_")) {
                    val districtName = data.substringAfter("district_") // Например "AVIA"
                    toggleDistrictSelection(chatId, districtName)
                    // Пересобираем те же кнопки (с отметками)
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

    /**
     * Показываем главное меню (Скрапить / Загрузить)
     */
    private fun showMainMenu(chatId: Long, text: String) {
        val buttons = InlineKeyboardMarkup.builder().keyboard(listOf(
            listOf(InlineKeyboardButton.builder().text("Скрапить учреждения").callbackData("DO_SCRAPE").build()),
            listOf(InlineKeyboardButton.builder().text("Загрузить Excel LandPlot").callbackData("DO_UPLOAD").build())
        )).build()

        val msg = SendMessage()
        msg.chatId = chatId.toString()
        msg.text = text
        msg.replyMarkup = buttons
        try {
            execute(msg)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    /**
     * Кнопки выбора формата (Текст или Excel)
     */
    private fun buildScrapeTypeButtons(): InlineKeyboardMarkup {
        val textBtn = InlineKeyboardButton.builder().text("Текст").callbackData("SCRAPE_TEXT").build()
        val excelBtn = InlineKeyboardButton.builder().text("Excel").callbackData("SCRAPE_EXCEL").build()

        return InlineKeyboardMarkup.builder().keyboard(
            listOf(
                listOf(textBtn, excelBtn)
            )
        ).build()
    }

    /**
     * Кнопки выбора районов (отмечаем выбранные)
     */
    private fun buildDistrictButtons(): InlineKeyboardMarkup {
        // Набор доступных районов
        val available = listOf("AVIA", "VAHI", "KIRO", "MOSC", "NOVO", "PRIV", "SOVI")
        // Формируем кнопки по 2-3 в строке для удобства
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        val rowSize = 3

        // Для каждого района делаем кнопку, если район уже выбран — помечаем символом "✓"
        available.chunked(rowSize).forEach { chunk ->
            val row = chunk.map { district ->
                val isSelected = userScrapeSettings.values.any { it.districts.contains(district) }
                val text = if (isSelected) "✔️ $district" else district
                InlineKeyboardButton.builder().text(text).callbackData("district_$district").build()
            }
            rows.add(row)
        }

        // Добавляем кнопку "Готово"
        val doneBtn = InlineKeyboardButton.builder().text("Готово").callbackData("SCRAPE_DISTRICT_DONE").build()
        rows.add(listOf(doneBtn))

        return InlineKeyboardMarkup.builder().keyboard(rows).build()
    }

    /**
     * При нажатии на район «переключаем» его в списке
     */
    private fun toggleDistrictSelection(chatId: Long, district: String) {
        val settings = userScrapeSettings[chatId] ?: ScrapeSettings()
        if (settings.districts.contains(district)) {
            settings.districts.remove(district)
        } else {
            settings.districts.add(district)
        }
        userScrapeSettings[chatId] = settings
    }

    /**
     * Выполняем фактический скрапинг
     */
    private fun performScrape(chatId: Long, messageId: Int) {
        val settings = userScrapeSettings[chatId] ?: ScrapeSettings()

        // Формируем URL
        val urlBuilder = StringBuilder("$apiBaseUrl/scrape?update=true")
        if (settings.excel) {
            urlBuilder.append("&excel=true")
        }
        if (settings.districts.isNotEmpty()) {
            val districtsParam = settings.districts.joinToString(",")
            urlBuilder.append("&districts=$districtsParam")
        }
        val finalUrl = urlBuilder.toString()

        // Меняем сообщение на "Подождите, идёт запрос..."
        editTextAndKeyboard(chatId, messageId, "Запуск скрапинга...", null)

        try {
            if (settings.excel) {
                val responseEntity = restTemplate.getForEntity(URI(finalUrl), ByteArray::class.java)
                if (responseEntity.statusCode.is2xxSuccessful) {
                    val fileBytes = responseEntity.body
                    if (fileBytes != null && fileBytes.isNotEmpty()) {
                        sendExcelDocument(chatId, fileBytes, "institutions.xlsx")
                    } else {
                        sendLongMessage(chatId.toString(), "Пустой ответ или не удалось получить файл.")
                    }
                } else {
                    sendLongMessage(chatId.toString(), "Ошибка при получении Excel-файла. Код: ${responseEntity.statusCodeValue}")
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
            // Возвращаем бота в состояние IDLE
            userStates[chatId] = BotState.IDLE
            userScrapeSettings[chatId] = ScrapeSettings()
        }
    }

    /**
     * Обрабатываем загрузку Excel-файла (LandPlot)
     */
    private fun handleFileUpload(document: Document, chatId: Long) {
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

    /**
     * Загрузка файла на наш сервис: /api/uploadLandplots?text=true
     */
    private fun uploadExcelToServer(fileBytes: ByteArray, originalFilename: String): String {
        val url = "$apiBaseUrl/uploadLandplots?text=true"

        val headers = org.springframework.http.HttpHeaders()
        headers.contentType = org.springframework.http.MediaType.MULTIPART_FORM_DATA
        val body = org.springframework.util.LinkedMultiValueMap<String, Any>()

        val fileResource = object : org.springframework.core.io.ByteArrayResource(fileBytes) {
            override fun getFilename() = originalFilename
        }

        body.add("file", fileResource)

        val requestEntity = org.springframework.http.HttpEntity(body, headers)
        val response = restTemplate.postForEntity(url, requestEntity, String::class.java)

        return if (response.statusCode.is2xxSuccessful && !response.body.isNullOrEmpty()) {
            response.body!!
        } else {
            "Не удалось загрузить данные. Код ответа: ${response.statusCodeValue}"
        }
    }

    /**
     * Удобный метод для изменения текста уже отправленного сообщения (чтобы не плодить новые)
     */
    private fun editTextAndKeyboard(chatId: Long, messageId: Int, newText: String, keyboard: InlineKeyboardMarkup?) {
        val editMessage = EditMessageText()
        editMessage.chatId = chatId.toString()
        editMessage.messageId = messageId
        editMessage.text = newText
        editMessage.replyMarkup = keyboard
        try {
            execute(editMessage)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    /**
     * Для скачивания пути к файлу на серверах Telegram
     */
    private fun downloadFilePath(fileId: String): String {
        val file = execute(org.telegram.telegrambots.meta.api.methods.GetFile(fileId))
        return file.filePath
    }

    /**
     * Скачиваем файл как массив байт
     */
    private fun downloadFileAsBytes(filePath: String): ByteArray {
        return downloadFileAsStream(filePath).readBytes()
    }

    /**
     * Отправка больших сообщений (если текст переваливает за лимит ~4096)
     */
    private fun sendLongMessage(chatId: String, text: String) {
        val parts = text.chunked(MAX_MESSAGE_LENGTH)
        for (part in parts) {
            val msg = SendMessage(chatId, part)
            try {
                execute(msg)
            } catch (e: TelegramApiException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Отправка Excel-файла
     */
    private fun sendExcelDocument(chatId: Long, fileBytes: ByteArray, fileName: String) {
        val inputStream = ByteArrayInputStream(fileBytes)
        val sendDocument = SendDocument()
        sendDocument.chatId = chatId.toString()
        sendDocument.document = InputFile(inputStream, fileName)
        sendDocument.caption = "Результаты в формате Excel"

        try {
            execute(sendDocument)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
            sendLongMessage(chatId.toString(), "Ошибка при отправке файла: ${e.message}")
        }
    }
}