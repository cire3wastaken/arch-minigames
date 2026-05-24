package mc.arch.pubapi.pigdi.controller

import mc.arch.pubapi.pigdi.dto.ErrorResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

@RestControllerAdvice
class RestExceptionHandler
{
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse>
    {
        val value = e.value?.toString() ?: "null"

        val (error, message) = when (e.requiredType)
        {
            UUID::class.java -> "INVALID_UUID" to "Invalid UUID format: '$value'"
            else -> "INVALID_PARAMETER" to "Invalid value for parameter '${e.name}': '$value'"
        }

        return ResponseEntity.badRequest().body(
            ErrorResponse(error = error, message = message)
        )
    }
}
