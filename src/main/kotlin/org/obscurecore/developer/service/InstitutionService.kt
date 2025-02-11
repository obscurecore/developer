package org.obscurecore.developer

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.obscurecore.developer.dto.InstitutionDetails
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Сервис для скрапинга и обработки данных образовательных учреждений.
 */
@Service
class InstitutionService {

    private val logger = LoggerFactory.getLogger(InstitutionService::class.java)
    private val csvFilePath = "institutions.csv"
    private val baseUrl = "https://edu.tatar.ru/index.htm"

    // Целевые районы (на русском)
    private val targetDistricts = setOf(
        "Авиастроительный", "Вахитовский", "Кировский", "Московский",
        "Ново-Савиновский", "Приволжский", "Советский"
    )

    /**
     * Выполняет скрапинг (при update=true) и возвращает список учреждений.
     */
    fun scrapeInstitutionsAsList(update: Boolean, districts: List<String>?): List<InstitutionDetails> {
        initializeCsvFile()

        if (update) {
            try {
                val mainDoc = fetchDocument(baseUrl)
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось загрузить главную страницу")
                val districtLinks = extractDistrictLinks(mainDoc)
                if (districtLinks.isEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось найти ссылки на районы")
                }

                val districtsToProcess = if (districts.isNullOrEmpty()) targetDistricts
                else targetDistricts.intersect(districts.map { it.trim() }.toSet())

                if (districtsToProcess.isEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Указанные районы не найдены в целевых районах"
                    )
                }

                val filteredDistrictLinks = districtLinks.filterKeys { it in districtsToProcess }
                if (filteredDistrictLinks.isEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Не найдены ссылки на указанные районы")
                }

                filteredDistrictLinks.forEach { (district, url) ->
                    processDistrict(district, url)
                }
            } catch (ex: Exception) {
                logger.error("Ошибка при обновлении данных: ${ex.message}", ex)
                throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера")
            }
        }

        return try {
            val allInstitutions = readCsvFile()
            if (!districts.isNullOrEmpty()) allInstitutions.filter { it.district in districts } else allInstitutions
        } catch (ex: Exception) {
            logger.error("Ошибка при чтении данных: ${ex.message}", ex)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при чтении данных")
        }
    }

    /**
     * Генерирует Excel-файл (XLSX) из списка учреждений.
     */
    fun generateExcel(institutions: List<InstitutionDetails>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Institutions")
        val header = sheet.createRow(0)
        header.createCell(0, CellType.STRING).setCellValue("ID")
        header.createCell(1, CellType.STRING).setCellValue("Тип")
        header.createCell(2, CellType.STRING).setCellValue("Номер")
        header.createCell(3, CellType.STRING).setCellValue("Количество учащихся")
        header.createCell(4, CellType.STRING).setCellValue("Район")
        header.createCell(5, CellType.STRING).setCellValue("Ссылка")

        institutions.forEachIndexed { index, inst ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(inst.id)
            row.createCell(1).setCellValue(inst.type)
            row.createCell(2).setCellValue(inst.number)
            row.createCell(3).setCellValue(inst.studentsCount)
            row.createCell(4).setCellValue(inst.district)
            row.createCell(5).setCellValue(inst.url)
        }

        return ByteArrayOutputStream().use { bos ->
            workbook.write(bos)
            workbook.close()
            bos.toByteArray()
        }
    }

    // Вспомогательные методы

    private fun fetchDocument(url: String): Document? =
        try {
            Jsoup.connect(url).get()
        } catch (ex: Exception) {
            logger.error("Ошибка загрузки страницы: $url. Причина: ${ex.message}", ex)
            null
        }

    private fun extractDistrictLinks(doc: Document): Map<String, String> {
        return doc.select("a")
            .filter { element -> element.text().trim() in targetDistricts }
            .associate { it.text().trim() to it.absUrl("href") }
    }

    private fun processDistrict(district: String, districtUrl: String) {
        logger.info("Обрабатывается район: $district")
        val districtDoc = fetchDocument(districtUrl) ?: run {
            logger.warn("Не удалось загрузить документ для района $district")
            return
        }
        processEducationType(district, districtDoc, "Школы", "Школа")
        processEducationType(district, districtDoc, "Дошкольное образование", "Детский сад")
    }

    private fun processEducationType(
        district: String,
        districtDoc: Document,
        educationType: String,
        institutionType: String
    ) {
        val educationUrl = fetchEducationLinks(districtDoc, educationType)
        if (educationUrl == null) {
            logger.warn("Ссылка на $educationType не найдена для района $district")
            return
        }
        val educationDoc = fetchDocument(educationUrl) ?: run {
            logger.warn("Не удалось загрузить документ для $educationType в районе $district")
            return
        }
        val institutionLinks = extractItems(educationDoc, institutionType)
        institutionLinks.forEach { url ->
            val id = extractInstitutionId(url)
            if (!isInstitutionInCsv(id)) {
                fetchInstitutionDetails(url, institutionType, district)?.let {
                    addInstitutionToCsv(it)
                    logger.info("Добавлено: Учреждение: ${it.type}, Номер: ${it.number}")
                }
            }
        }
    }

    private fun fetchEducationLinks(doc: Document, educationType: String): String? {
        return doc.selectFirst("a:has(span:contains($educationType))")?.absUrl("href")
    }

    private fun extractItems(doc: Document, keyword: String): List<String> {
        return doc.select("a")
            .filter { element ->
                val text = element.text().trim()
                text.contains(keyword, ignoreCase = true) || text.matches(Regex(".*\\d{1,3}.*"))
            }
            .map { it.absUrl("href") }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun fetchInstitutionDetails(url: String, institutionType: String, district: String): InstitutionDetails? {
        val doc = fetchDocument(url) ?: return null
        val shortName = doc.selectFirst("div:contains(Короткое название:)")?.text()?.trim() ?: "Неизвестное учреждение"
        val number = Regex("№\\s?(\\d+)").find(shortName)?.groupValues?.get(1) ?: "Без номера"
        val text = doc.selectFirst("div:contains(У нас учатся)")?.text() ?: ""
        val totalStudentsCount = extractStudentCount(text)
        return InstitutionDetails(
            id = extractInstitutionId(url),
            type = institutionType,
            number = number,
            studentsCount = totalStudentsCount.toString(),
            district = district,
            url = url
        )
    }

    private fun extractStudentCount(text: String): Int {
        val patterns = listOf(
            Regex("Воспитанников[:]?\\s*(\\d+)"),
            Regex("Иностранных граждан[:]?\\s*(-?\\d+)"),
            Regex("У нас учатся[:]?\\s*(\\d+)\\s*воспитанников"),
            Regex("У нас учатся[:]?\\s*(\\d+)\\s*обучающихся"),
            Regex("У нас учатся[:]?\\s*(\\d+)")
        )
        return patterns.mapNotNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
        }.sum()
    }

    private fun readCsvFile(): List<InstitutionDetails> {
        val file = File(csvFilePath)
        if (!file.exists()) return emptyList()
        CSVReader(FileReader(file)).use { reader ->
            return reader.readAll().drop(1).mapNotNull { parts ->
                if (parts.size == 6) InstitutionDetails(
                    id = parts[0],
                    type = parts[1],
                    number = parts[2],
                    studentsCount = parts[3],
                    district = parts[4],
                    url = parts[5]
                ) else null
            }
        }
    }

    private fun initializeCsvFile() {
        val file = File(csvFilePath)
        if (!file.exists()) {
            CSVWriter(FileWriter(file, false)).use { writer ->
                writer.writeNext(arrayOf("ID", "Тип учреждения", "Номер", "Количество учащихся", "Район", "Ссылка"))
            }
            logger.info("Создан новый CSV-файл по пути $csvFilePath")
        }
    }

    private fun isInstitutionInCsv(institutionId: String): Boolean {
        val file = File(csvFilePath)
        if (!file.exists()) return false
        CSVReader(FileReader(file)).use { reader ->
            return reader.readAll().any { it[0] == institutionId }
        }
    }

    private fun addInstitutionToCsv(institution: InstitutionDetails) {
        CSVWriter(FileWriter(csvFilePath, true)).use { writer ->
            writer.writeNext(
                arrayOf(
                    institution.id,
                    institution.type,
                    institution.number,
                    institution.studentsCount,
                    institution.district,
                    institution.url
                )
            )
        }
    }

    private fun extractInstitutionId(url: String): String {
        return url.substringAfterLast("/").substringBefore(".")
    }
}