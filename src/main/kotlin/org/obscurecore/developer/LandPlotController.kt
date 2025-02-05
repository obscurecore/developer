package org.obscurecore.developer

import LandPlot
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api")
class LandPlotController(private val landPlotService: LandPlotService) {

    private val logger = LoggerFactory.getLogger(LandPlotController::class.java)

    @Operation(
        summary = "Загрузка данных земельных участков из Excel файла",
        description = """
            Метод принимает Excel файл (XLSX) с данными о земельных участках.
            При успешной обработке возвращает либо JSON, либо текст (если text=true).
        """
    )
    @PostMapping("/uploadLandplots", consumes = ["multipart/form-data"])
    fun uploadLandPlots(
        @Parameter(description = "Excel файл (XLSX) с данными земельных участков", required = true)
        @RequestPart("file") file: MultipartFile,

        @Parameter(description = "Возвращать результат в текстовом формате", example = "false")
        @RequestParam(required = false, defaultValue = "false") text: Boolean
    ): ResponseEntity<Any> {
        logger.info("Получен запрос на загрузку файла: ${file.originalFilename}")

        val landPlots = landPlotService.uploadLandPlotsAsList(file)
        if (landPlots.isEmpty()) {
            return if (text) {
                ResponseEntity.ok("Не удалось загрузить или распознать данные земельных участков.")
            } else {
                ResponseEntity.ok(landPlots)
            }
        }

        return if (text) {
            val sb = StringBuilder("Загруженные земельные участки:\n")
            landPlots.forEach { lp ->
                sb.append("---------------\n")
                sb.append("• ID участка: ${lp.plotId ?: "—"}\n")
                sb.append("• Назначение: ${lp.purpose ?: "—"}\n")
                sb.append("• Площадь (м²): ${lp.area ?: "—"}\n")
                sb.append("• Кадастровый номер: ${lp.cadastralNumber ?: "—"}\n")
                sb.append("• Проект: ${lp.project ?: "—"}\n")
            }
            ResponseEntity.ok(sb.toString())
        } else {
            ResponseEntity.ok(landPlots)
        }
    }
}