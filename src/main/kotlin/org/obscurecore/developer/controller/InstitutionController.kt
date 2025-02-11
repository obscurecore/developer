package org.obscurecore.developer.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.obscurecore.developer.InstitutionService
import org.obscurecore.developer.dto.District
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер для получения и обновления данных образовательных учреждений.
 */
@RestController
@RequestMapping("/api")
class InstitutionController(private val institutionService: InstitutionService) {

    private val logger = LoggerFactory.getLogger(InstitutionController::class.java)

    @Operation(
        summary = "Получение и обновление данных образовательных учреждений",
        description = """
            Инициирует процесс скрапинга данных об образовательных учреждениях.
            Параметр update отвечает за обновление через скрапинг (true/false).
            Параметр districts позволяет указать список районов (например, AVIA, VAHI и т.д.).
            Параметр excel выбирает формат результата:
            - false (по умолчанию) возвращает человеко-читаемый текст;
            - true возвращает Excel-файл (XLSX) в бинарном формате.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Успешное получение данных об учреждениях"),
            ApiResponse(responseCode = "400", description = "Неверный запрос или отсутствуют необходимые данные"),
            ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
        ]
    )
    @GetMapping("/scrape", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.TEXT_PLAIN_VALUE])
    fun scrapeInstitutions(
        @Parameter(
            description = "Флаг обновления данных через скрапинг",
            example = "true"
        )
        @RequestParam(required = false, defaultValue = "true") update: Boolean,

        @Parameter(
            description = "Список районов (AVIA, VAHI, KIRO, MOSC, NOVO, PRIV, SOVI)",
            schema = Schema(
                implementation = District::class,
                allowableValues = ["AVIA", "VAHI", "KIRO", "MOSC", "NOVO", "PRIV", "SOVI"]
            )
        )
        @RequestParam(required = false) districts: List<District>?,

        @Parameter(
            description = "Формат результата: Excel (true) или текст (false)",
            example = "false"
        )
        @RequestParam(required = false, defaultValue = "false") excel: Boolean
    ): ResponseEntity<Any> {
        logger.info("Запрос /scrape: update=$update, districts=$districts, excel=$excel")

        val districtNames: List<String>? = districts?.map { it.value }
        val institutions = institutionService.scrapeInstitutionsAsList(update, districtNames)

        return if (excel) {
            val excelBytes = institutionService.generateExcel(institutions)
            val resource = ByteArrayResource(excelBytes)
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=institutions.xlsx")
                .body(resource)
        } else {
            val resultText = buildString {
                if (institutions.isEmpty()) {
                    append("Нет данных об образовательных учреждениях.\n")
                } else {
                    institutions.forEach { inst ->
                        append("Учреждение:\n")
                        append("• ID: ${inst.id}\n")
                        append("• Тип: ${inst.type}\n")
                        append("• Номер: ${inst.number}\n")
                        append("• Количество учащихся: ${inst.studentsCount}\n")
                        append("• Район: ${inst.district}\n")
                        append("• Ссылка: ${inst.url}\n")
                        append("-------------------------\n")
                    }
                }
            }
            ResponseEntity.ok(resultText)
        }
    }
}