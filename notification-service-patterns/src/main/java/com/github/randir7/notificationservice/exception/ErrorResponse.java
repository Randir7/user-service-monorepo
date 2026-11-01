package com.github.randir7.notificationservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Единый формат тела HTTP-ответа с ошибкой.
 *
 * <p>Структура идентична контракту ErrorResponse из user-service:
 * клиентам обоих сервисов достаточно одного парсера ошибок.
 * Поле errors присутствует только при ошибках валидации полей
 * (null-значения исключаются из JSON аннотацией).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> errors
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, null);
    }
    
    public static ErrorResponse of(int status, String error, String message, String path,
                                   Map<String, String> errors) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, errors);
    }
}