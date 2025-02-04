package org.obscurecore.developer

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

@Service
class InstitutionService {

    private val logger = LoggerFactory.getLogger(InstitutionService::class.java)
    private val csvFilePath = "institutions.csv" // Рекомендуется вынести в application.properties
    private val baseUrl = "https://edu.tatar.ru/index.htm"
    private val targetDistricts = setOf(
        "Авиастроительный",
        "Вахитовский",
        "Кировский",
        "Московский",
        "Ново-Савиновский",
        "Приволжский",
        "Советский"
    )

    fun scrapeInstitutions(update: Boolean, districts: List<String>?): ResponseEntity<List<InstitutionDetails>> {
        initializeCsvFile()

        if (update) {
            try {
                val mainDoc = fetchDocument(baseUrl) ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Не удалось загрузить главную страницу"
                )

                val districtLinks = extractDistrictLinks(mainDoc)
                if (districtLinks.isEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось найти ссылки на районы")
                }

                // Определение районов для обработки
                val districtsToProcess = if (districts.isNullOrEmpty()) {
                    targetDistricts
                } else {
                    targetDistricts.intersect(districts.map { it.trim() }.toSet())
                }

                if (districtsToProcess.isEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Указанные районы не найдены в целевых районах"
                    )
                }

                val filteredDistrictLinks = districtLinks.filterKeys { it in districtsToProcess }

                if (filteredDistrictLinks.isEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Не найдены ссылки на указанные районы"
                    )
                }

                filteredDistrictLinks.forEach { (district, districtUrl) ->
                    processDistrict(district, districtUrl)
                }

            } catch (e: Exception) {
                logger.error("Ошибка при обновлении данных: ${e.message}", e)
                throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера")
            }
        }

        return try {
            val institutions = readCsvFile()
            val filteredInstitutions = if (!districts.isNullOrEmpty()) {
                institutions.filter { it.district in districts }
            } else {
                institutions
            }
            ResponseEntity.ok(filteredInstitutions)
        } catch (e: Exception) {
            logger.error("Ошибка при чтении данных: ${e.message}", e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при чтении данных")
        }
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
        val educationUrl = fetchEducationLinks(districtDoc, educationType) ?: run {
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

    private fun fetchDocument(url: String): Document? {
        return try {
            Jsoup.connect(url).get()
        } catch (e: Exception) {
            logger.error("Ошибка загрузки страницы: $url. Причина: ${e.message}", e)
            null
        }
    }

    private fun extractDistrictLinks(mainDoc: Document): Map<String, String> {
        return mainDoc.select("a")
            .filter { element -> element.text().trim() in targetDistricts }
            .associate { element -> element.text().trim() to element.absUrl("href") }
    }

    private fun fetchEducationLinks(districtDoc: Document, educationType: String): String? {
        val educationLinkElement = districtDoc.selectFirst("a:has(span:contains($educationType))")
        return educationLinkElement?.absUrl("href")
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

    private fun isInstitutionInCsv(institutionId: String): Boolean {
        val file = File(csvFilePath)
        if (!file.exists()) return false
        CSVReader(FileReader(file)).use { reader ->
            return reader.readAll().any { it[0] == institutionId }
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

    private fun fetchInstitutionDetails(
        institutionUrl: String,
        institutionType: String,
        district: String
    ): InstitutionDetails? {
        val doc = fetchDocument(institutionUrl) ?: return null
        val shortName = doc.selectFirst("div:contains(Короткое название:)")?.text()?.trim() ?: "Неизвестное учреждение"
        val number = Regex("№\\s?(\\d+)").find(shortName)?.groupValues?.get(1) ?: "Без номера"
        val text = doc.selectFirst("div:contains(У нас учатся)")?.text() ?: ""
        val totalStudentsCount = extractStudentCount(text)

        return InstitutionDetails(
            id = extractInstitutionId(institutionUrl),
            type = institutionType,
            number = number,
            studentsCount = totalStudentsCount.toString(),
            district = district,
            url = institutionUrl
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

        var total = 0
        patterns.forEach { pattern ->
            pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { total += it }
        }
        return total
    }
}