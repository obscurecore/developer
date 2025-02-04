package org.obscurecore.developer

import LandPlot
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api")
class LandPlotController(private val landPlotService: LandPlotService) {

    private val logger = LoggerFactory.getLogger(LandPlotController::class.java)

    @Operation(
        summary = "Загрузка данных земельных участков из Excel файла",
        description = "Метод принимает Excel файл с данными о земельных участках. " +
                "Файл должен иметь формат XLSX и содержать корректно заполненные столбцы, например: " +
                "fid, ВРИ, Площадь, Кадастровый номер, Проект, и т.д. " +
                "При успешной обработке возвращается список объектов LandPlot, содержащих детальную информацию."
    )
    @PostMapping("/uploadLandplots", consumes = ["multipart/form-data"])
    fun uploadLandPlots(
        @Parameter(
            description = "Excel файл с данными земельных участков. Формат: XLSX",
            required = true
        )
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<List<LandPlot>> {
        logger.info("Получен запрос на загрузку файла: ${file.originalFilename}")
        return landPlotService.uploadLandPlots(file)
    }
}