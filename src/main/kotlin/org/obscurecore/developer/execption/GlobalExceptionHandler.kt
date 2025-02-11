package org.obscurecore.developer.execption

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException

/**
 * Глобальный обработчик исключений для приложения.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Обработка исключений типа ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<Any> {
        logger.error("Ошибка: ${ex.reason}", ex)
        return ResponseEntity.status(ex.statusCode).body(mapOf("error" to ex.reason))
    }

    /**
     * Обработка всех остальных исключений.
     */
    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception): ResponseEntity<Any> {
        logger.error("Внутренняя ошибка сервера: ${ex.message}", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "Внутренняя ошибка сервера"))
    }
}