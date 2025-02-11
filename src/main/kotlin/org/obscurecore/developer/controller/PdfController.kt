package org.obscurecore.developer

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.obscurecore.developer.service.PdfImageService
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Контроллер для извлечения изображений из PDF.
 */
@RestController
@RequestMapping("/api")
class PdfController(private val pdfImageService: PdfImageService) {

    private val logger = LoggerFactory.getLogger(PdfController::class.java)

    @Operation(
        summary = "Извлечение изображений из PDF",
        description = "Загружает PDF-документ, извлекает изображения и возвращает архив (ZIP) с изображениями.",
        responses = [
            ApiResponse(
                responseCode = "200", description = "Изображения успешно извлечены",
                content = [Content(mediaType = "application/zip", schema = Schema(implementation = ByteArray::class))]
            ),
            ApiResponse(responseCode = "400", description = "Некорректный PDF-файл"),
            ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @PostMapping("/extractPdfImages", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun extractPdfImages(@org.springframework.web.bind.annotation.RequestPart("file") file: MultipartFile): ResponseEntity<Any> {
        logger.info("Получен запрос на извлечение изображений из PDF: ${file.originalFilename}")
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Файл не должен быть пустым"))
        }
        if (file.contentType != "application/pdf") {
            return ResponseEntity.badRequest().body(mapOf("error" to "Файл должен быть PDF-документом"))
        }
        return try {
            val zipBytes = pdfImageService.extractImages(file.inputStream)
            val resource = ByteArrayResource(zipBytes)
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=extracted_images.zip")
                .body(resource)
        } catch (ex: Exception) {
            logger.error("Ошибка при извлечении изображений: ${ex.message}", ex)
            ResponseEntity.status(500).body(mapOf("error" to "Внутренняя ошибка сервера"))
        }
    }
}