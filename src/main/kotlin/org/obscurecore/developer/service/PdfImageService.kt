package org.obscurecore.developer.service

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Сервис для извлечения изображений из PDF-документов.
 */
@Service
class PdfImageService {

    private val logger = LoggerFactory.getLogger(PdfImageService::class.java)

    /**
     * Извлекает изображения из PDF и возвращает архив (ZIP) в виде массива байт.
     */
    fun extractImages(pdfInputStream: java.io.InputStream): ByteArray {
        val imagesZip = ByteArrayOutputStream()
        ZipOutputStream(imagesZip).use { zipOut ->
            PDDocument.load(pdfInputStream).use { document ->
                var pageIndex = 0
                for (page in document.pages) {
                    pageIndex++
                    val resources = page.resources
                    val xObjectNames = resources.xObjectNames
                    var imageCount = 0
                    for (xObjectName in xObjectNames) {
                        val xObject = resources.getXObject(xObjectName)
                        if (xObject is PDImageXObject) {
                            imageCount++
                            val bufferedImage: BufferedImage = xObject.image
                            val imageName = "extracted_page${pageIndex}_img${imageCount}.png"
                            val imageBytes = ByteArrayOutputStream().use { imgOut ->
                                ImageIO.write(bufferedImage, "png", imgOut)
                                imgOut.toByteArray()
                            }
                            zipOut.putNextEntry(ZipEntry(imageName))
                            zipOut.write(imageBytes)
                            zipOut.closeEntry()
                            logger.info("Изображение добавлено в архив: $imageName")
                        }
                    }
                    if (imageCount == 0) {
                        logger.info("На странице $pageIndex не найдено изображений.")
                    }
                }
            }
        }
        return imagesZip.toByteArray()
    }
}