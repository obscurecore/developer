// File: InstitutionController.kt
package org.obscurecore.developer.controller

import org.obscurecore.developer.InstitutionService
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

    @Operation(summary = "Scrape institutions data")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved institutions"),
            ApiResponse(responseCode = "400", description = "Bad request"),
            ApiResponse(responseCode = "500", description = "Internal server error")
        ]
    )
    @GetMapping("/scrape")
    fun scrapeInstitutions(
        @Parameter(description = "Whether to update the data", example = "true")
        @RequestParam(required = false, defaultValue = "true") update: Boolean,

        @Parameter(description = "List of districts to filter institutions", example = "Авиастроительный,Вахитовский")
        @RequestParam(required = false) districts: List<String>?
    ): ResponseEntity<Any> {
        return institutionService.scrapeInstitutions(update, districts)
            .also { logger.info("Scrape institutions called with update=$update and districts=$districts") }
    }
}