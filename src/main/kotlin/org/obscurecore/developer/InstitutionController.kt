package org.obscurecore.developer

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses

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
                    "Поддерживаемые значения: Авиастроительный, Вахитовский, Кировский, Московский, " +
                    "Ново-Савиновский, Приволжский, Советский",
            example = "Авиастроительный,Вахитовский"
        )
        @RequestParam(required = false) districts: List<String>?
    ): ResponseEntity<List<InstitutionDetails>> {
        return institutionService.scrapeInstitutions(update, districts)
            .also { logger.info("Запрос скрапинга учреждений с update=$update и districts=$districts") }
    }
}