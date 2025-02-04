package org.obscurecore.developer

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.media.Schema

@RestController
@RequestMapping("/api")
class InstitutionController(private val institutionService: InstitutionService) {

    private val logger = LoggerFactory.getLogger(InstitutionController::class.java)

    @Operation(
        summary = "Получение и обновление данных образовательных учреждений",
        description = "Метод инициирует процесс скрапинга данных об образовательных учреждениях. " +
                "Если параметр update=true, данные обновляются путём скрапинга сайта. " +
                "Если указан параметр districts, производится фильтрация по указанным районам. " +
                "В случае успешного выполнения возвращается список учреждений, представленный объектами InstitutionDetails. " +
                "Возможные коды ответов: 200 - Данные успешно получены, 400 - Неверный запрос, 500 - Внутренняя ошибка сервера"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Успешное получение данных об учреждениях"),
            ApiResponse(responseCode = "400", description = "Неверный запрос или отсутствуют необходимые данные"),
            ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера при обработке запроса")
        ]
    )
    @GetMapping("/scrape")
    fun scrapeInstitutions(
        @Parameter(
            description = "Флаг, указывающий, требуется ли обновление данных. " +
                    "При значении true данные будут обновлены путём скрапинга сайта.",
            example = "true"
        )
        @RequestParam(required = false, defaultValue = "true") update: Boolean,

        @Parameter(
            description = "Список районов для фильтрации образовательных учреждений. " +
                    "Допустимые значения: AVIA, VAHI, KIRO, MOSC, NOVO, PRIV, SOVI",
            schema = Schema(
                implementation = District::class,
                allowableValues = ["AVIA", "VAHI", "KIRO", "MOSC", "NOVO", "PRIV", "SOVI"]
            )
        )
        @RequestParam(required = false) districts: List<District>?
    ): ResponseEntity<List<InstitutionDetails>> {
        // Преобразуем полученный список District в список строк для обработки в сервисе
        val districtNames: List<String>? = districts?.map { it.value }
        return institutionService.scrapeInstitutions(update, districtNames)
            .also { logger.info("Запрос скрапинга учреждений с update=$update и districts=${districtNames}") }
    }
}