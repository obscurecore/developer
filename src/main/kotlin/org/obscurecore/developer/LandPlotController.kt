// File: LandPlotController.kt
package org.obscurecore.developer

import io.swagger.v3.oas.annotations.Operation
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

    @Operation(summary = "Upload Excel file containing land plots data")
    @PostMapping("/uploadLandplots", consumes = ["multipart/form-data"])
    fun uploadLandPlots(
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<List<LandPlot>> {
        logger.info("Received upload request for file: ${file.originalFilename}")
        return landPlotService.uploadLandPlots(file)
    }
}