package org.obscurecore.developer

import java.io.InputStream
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class LandPlotService {

    private val logger = LoggerFactory.getLogger(LandPlotService::class.java)

    fun uploadLandPlots(file: MultipartFile): ResponseEntity<List<LandPlot>> {
        if (file.isEmpty) {
            logger.warn("Uploaded file is empty")
            return ResponseEntity.badRequest().body(emptyList())
        }

        if (!isExcelFile(file)) {
            logger.warn("Uploaded file is not an Excel file")
            return ResponseEntity.badRequest().body(emptyList())
        }

        return try {
            val landPlots = readExcelData(file.inputStream)
            logger.info("Successfully processed ${landPlots.size} land plots from file ${file.originalFilename}")
            ResponseEntity.ok(landPlots)
        } catch (e: Exception) {
            logger.error("Error processing file ${file.originalFilename}: ${e.message}", e)
            ResponseEntity.badRequest().build()
        }
    }

    private fun isExcelFile(file: MultipartFile): Boolean {
        val contentType = file.contentType
        return contentType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    fun readExcelData(inputStream: InputStream): List<LandPlot> {
        val plots = mutableListOf<LandPlot>()
        XSSFWorkbook(inputStream).use { workbook ->
            val sheet = workbook.getSheetAt(0) ?: throw IllegalArgumentException("Sheet is empty")
            val header = sheet.getRow(0) ?: throw IllegalArgumentException("Header row is missing")
            val columnIndexes = header.mapIndexed { index, cell -> getCellStringValue(cell) to index }.toMap()

            for (row in sheet) {
                if (row.rowNum == 0) continue // Пропустить заголовок

                val landPlot = LandPlot(
                    plotId = row.getCellValue(columnIndexes["fid"]),
                    purpose = row.getCellValue(columnIndexes["ВРИ"]),
                    area = row.getCellValue(columnIndexes["Площадь"])?.toDoubleOrNull(),
                    cadastralNumber = row.getCellValue(columnIndexes["Кадастровый номер"]),
                    project = row.getCellValue(columnIndexes["Проект"]),
                    crossAnalysisField2 = row.getCellValue(columnIndexes["Участки на кросс анализ_field_2"]),
                    genplanId = row.getCellValue(columnIndexes["Genplan_fid"]),
                    genplanZone = row.getCellValue(columnIndexes["Genplan_Генплан"]),
                    genplanZoneNumber = row.getCellValue(columnIndexes["Genplan_Номер зоны Генплана"]),
                    genplanPlanId = row.getCellValue(columnIndexes["Genplan_ID Генплана"]),
                    oknId = row.getCellValue(columnIndexes["ОКН_fid"]),
                    okn = row.getCellValue(columnIndexes["ОКН_ОКН"]),
                    zoningId = row.getCellValue(columnIndexes["ЗРЗ_fid"]),
                    zoning = row.getCellValue(columnIndexes["ЗРЗ_ЗРЗ"]),
                    zoningHeightRestriction = row.getCellValue(columnIndexes["ЗРЗ_Ограничение высоты"]),
                    icgfoId = row.getCellValue(columnIndexes["ИЦГФО_fid"]),
                    icgfo = row.getCellValue(columnIndexes["ИЦГФО_ИЦГФО"]),
                    pptId = row.getCellValue(columnIndexes["ППТ и ППиМТ_fid"]),
                    ppt = row.getCellValue(columnIndexes["ППТ и ППиМТ_ППТ"]),
                    oknTerritoryId = row.getCellValue(columnIndexes["Территория ОКН_fid"]),
                    oknTerritory = row.getCellValue(columnIndexes["Территория ОКН_Территория ОКН"]),
                    crossAnalysisField1 = row.getCellValue(columnIndexes["Участки на кросс анализ_field_1"]),
                    recreationalComplexId = row.getCellValue(columnIndexes["Природно-рекреационный комплекс_fid"]),
                    recreationalComplex = row.getCellValue(columnIndexes["Природно-рекреационный комплекс_Природно-рекреационный комплекс"]),
                    pzzSubzoneId = row.getCellValue(columnIndexes["Подзоны ПЗЗ_fid"]),
                    pzzSubzone = row.getCellValue(columnIndexes["Подзоны ПЗЗ_Подзона ПЗЗ"]),
                    pzzSubzoneShort = row.getCellValue(columnIndexes["Подзоны ПЗЗ_Сокращение подзоны ПЗЗ"]),
                    pzzId = row.getCellValue(columnIndexes["ПЗЗ_fid"]),
                    pzz = row.getCellValue(columnIndexes["ПЗЗ_ПЗЗ"])
                )
                plots.add(landPlot)
            }
        }
        return plots
    }

    private fun getCellStringValue(cell: org.apache.poi.ss.usermodel.Cell): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.cellFormula
            else -> ""
        }
    }

    private fun org.apache.poi.ss.usermodel.Row.getCellValue(index: Int?): String? {
        return index?.let {
            val cell = getCell(it)
            cell?.let { c ->
                when (c.cellType) {
                    CellType.STRING -> c.stringCellValue.trim().takeIf { it.isNotEmpty() }
                    CellType.NUMERIC -> c.numericCellValue.toString().trim()
                    CellType.BOOLEAN -> c.booleanCellValue.toString()
                    CellType.FORMULA -> c.cellFormula.trim()
                    else -> null
                }
            }
        }
    }
}