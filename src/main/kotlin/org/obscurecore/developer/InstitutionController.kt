package org.obscurecore.developer

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class InstitutionController(private val institutionService: InstitutionService) {

    private val logger = LoggerFactory.getLogger(InstitutionController::class.java)

    @Operation(
        summary = "Получение и обновление данных образовательных учреждений",
        description = """
            Метод инициирует процесс скрапинга данных об образовательных учреждениях.
            Параметр update отвечает за обновление через скрапинг (true/false).
            
            Параметр districts позволяет указать список районов (например, AVIA, VAHI и т.д.).
            
            Параметр excel позволяет выбрать формат результата:
            - false (по умолчанию) возвращает человеко-читаемый текст (а не JSON);
            - true возвращает Excel-файл (XLSX) в бинарном формате.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Успешное получение данных об учреждениях"),
            ApiResponse(responseCode = "400", description = "Неверный запрос или отсутствуют необходимые данные"),
            ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера при обработке запроса")
        ]
    )
    @GetMapping("/scrape", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.TEXT_PLAIN_VALUE])
    fun scrapeInstitutions(
        @Parameter(
            description = "Флаг, указывающий, требуется ли обновление данных.",
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
            description = "Вернуть результат в виде Excel-файла (true) или текстом (false)",
            example = "false"
        )
        @RequestParam(required = false, defaultValue = "false") excel: Boolean
    ): ResponseEntity<Any> {
        logger.info("Запрос /scrape: update=$update, districts=$districts, excel=$excel")

        // Преобразуем перечень районов в список строк
        val districtNames: List<String>? = districts?.map { it.value }
        // Получаем список учреждений
        val institutions = institutionService.scrapeInstitutionsAsList(update, districtNames)

        return if (excel) {
            // Формируем Excel-файл
            val excelBytes = institutionService.generateExcel(institutions)
            val resource = ByteArrayResource(excelBytes)

            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=institutions.xlsx")
                .body(resource)
        } else {
            // Формируем человеко-читаемый текст на русском
            val sb = StringBuilder()
            if (institutions.isEmpty()) {
                sb.append("Нет данных об образовательных учреждениях.\n")
            } else {
                institutions.forEach { inst ->
                    sb.append("Учреждение:\n")
                    sb.append("• ID: ${inst.id}\n")
                    sb.append("• Тип: ${inst.type}\n")
                    sb.append("• Номер: ${inst.number}\n")
                    sb.append("• Количество учащихся: ${inst.studentsCount}\n")
                    sb.append("• Район: ${inst.district}\n")
                    sb.append("• Ссылка: ${inst.url}\n")
                    sb.append("-------------------------\n")
                }
            }
            ResponseEntity.ok(sb.toString())
        }
    }
}