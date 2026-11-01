package com.github.randir7.userservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений для всех REST-контроллеров.
 *
 * <p><b>@RestControllerAdvice</b> делает две вещи одновременно:
 * <ol>
 *   <li><b>@ControllerAdvice</b> — регистрирует класс как AOP-аспект,
 *       который перехватывает исключения из всех @Controller-классов;</li>
 *   <li><b>@ResponseBody</b> — говорит, что возвращаемые объекты
 *       нужно сериализовать в тело ответа (в JSON через Jackson).</li>
 * </ol>
 *
 * <p><b>Порядок срабатывания обработчиков:</b> Spring сначала ищет
 * обработчик для ТОЧНОГО класса исключения, затем для его родителя,
 * затем для родителя родителя и т.д. Поэтому метод handleNotFound
 * (для UserNotFoundException) вызывается раньше, чем handleUnexpected
 * (для RuntimeException).
 *
 * <p><b>Принцип "шумного 500, тихого 4xx":</b>
 * - 500 (Internal Server Error) — это ЧП, нужно логировать ERROR-уровнем
 *   со всем стектрейсом, чтобы разработчик узнал и починил.
 * - 400/404/409 — это ошибки клиента, это НОРМАЛЬНАЯ работа API.
 *   Логируется только DEBUG (или вообще без логов) — иначе продакшен-логи
 *   захлебнутся от каждой опечатки пользователя.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Пользователь не найден (UserNotFoundException) → 404 Not Found.
     * Клиент получит понятное сообщение "Пользователь с id=999 не найден".
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(UserNotFoundException ex,
                                                        HttpServletRequest request) {
        log.debug("Пользователь не найден: {} {}", request.getMethod(), request.getRequestURI());
        
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
    
    /**
     * Нарушение бизнес-валидации (ValidationException) → 409 Conflict.
     *
     * <p>Почему 409, а не 400?
     * - 400 = "синтаксическая ошибка запроса" (неверный JSON, невалидные поля);
     * - 409 = "запрос синтаксически верный, но конфликтует с текущим состоянием
     *          ресурса" — то что надо: email занят другим пользователем.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex,
                                                          HttpServletRequest request) {
        log.debug("Бизнес-валидация не пройдена: {}", ex.getMessage());
        
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
    
    /**
     * Ошибки Bean Validation (@NotBlank, @Email, @Min и т.д.) → 400 Bad Request.
     *
     * <p>MethodArgumentNotValidException выкидывается Spring, когда @Valid
     * перед @RequestBody находит нарушения. Внутри есть BindingResult
     * со всеми найденными ошибками — они извлекаются и группируются по полям.
     *
     * <p>Результат — ответ:
     * "errors": {"email": "Некорректный формат", "age": "Возраст не может быть отрицательным"}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        // Сбор ошибок: имя поля → текст нарушения
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            // Если на одном поле несколько нарушений, берется только первое.
            // putIfAbsent предотвращает перезапись, если для одного поля
            // есть @NotBlank + @Size + @Email и сработали все три.
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        });
        
        log.debug("Ошибки валидации: {}", fieldErrors);
        
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Ошибка валидации входных данных",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }
    
    /**
     * Неверный тип path variable → 400 Bad Request.
     *
     * <p>Пример: GET /api/v1/users/abc вместо /api/v1/users/123.
     * Spring не сможет сконвертировать "abc" в Long и бросит
     * MethodArgumentTypeMismatchException. Без этого обработчика
     * клиент получил бы код 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        
        log.debug("Неверный тип параметра: {}={}", ex.getName(), ex.getValue());
        
        String message = String.format(
                "Параметр '%s' имеет неверное значение '%s'. Ожидался тип %s",
                ex.getName(),
                ex.getValue(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?"
        );
        
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }
    
    /**
     * Некорректный JSON в теле запроса → 400 Bad Request.
     *
     * <p>HttpMessageNotReadableException возникает, когда Jackson
     * не может распарсить JSON: лишние запятые, пропущенные кавычки,
     * несовместимые типы (строка вместо числа) и т.д.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        log.debug("Некорректный JSON: {}", ex.getMessage());
        
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Тело запроса содержит некорректный JSON",
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }
    
    /**
     * Все остальные необработанные исключения → 500 Internal Server Error.
     *
     * <p>Это "страховка": если что-то пойдет не так и ни один из обработчиков
     * выше не сработал, то все равно вернется корректный ErrorResponse,
     * а НЕ стектрейс Spring с кучей внутренней информации.
     *
     * <p> Здесь логируется ERROR с полным стектрейсом!
     * 500 — это реальная проблема в коде, ее нужно замечать и чинить.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        log.error("Необработанная ошибка при {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Внутренняя ошибка сервера",  // НЕ отдаем клиенту ex.getMessage()!
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}