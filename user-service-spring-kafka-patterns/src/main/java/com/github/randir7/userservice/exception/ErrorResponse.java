package com.github.randir7.userservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Единый формат тела HTTP-ответа с ошибкой.
 *
 * <p>Любая ошибка API возвращает JSON именно такой структуры — клиенту
 * (фронтенду, мобильному приложению) не нужно угадывать формат,
 * он всегда знает, что искать: status, message, timestamp, errors.
 *
 * <p>Примеры:
 *
 * <pre>
 * {
 *   "timestamp": "2026-10-10T21:30:15.123",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Пользователь с id=999 не найден",
 *   "path": "/api/v1/users/999"
 * }
 *
 * {
 *   "timestamp": "2026-10-10T21:31:02.456",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Ошибка валидации входных данных",
 *   "path": "/api/v1/users",
 *   "errors": {
 *     "email": "Некорректный формат email",
 *     "age": "Возраст не может быть отрицательным"
 *   }
 * }
 * </pre>
 *
 * <p><b>@JsonInclude(JsonInclude.Include.NON_NULL)</b> — Jackson не станет
 * сериализовать поля со значением null. Если ошибок по полям нет (например,
 * UserNotFoundException) — поле errors просто не попадет в JSON,
 * а не будет выглядеть как "errors": null.
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
    /**
     * Фабричный метод: конструирует ErrorResponse без детальных ошибок по полям.
     * Используется для большинства случаев (404, 409, 500 и т.д.).
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, null);
    }
    
    /**
     * Фабричный метод: конструирует ErrorResponse с разбивкой ошибок по полям.
     * Используется при провале Bean Validation.
     */
    public static ErrorResponse of(int status, String error, String message, String path,
                                   Map<String, String> errors) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, errors);
    }
}