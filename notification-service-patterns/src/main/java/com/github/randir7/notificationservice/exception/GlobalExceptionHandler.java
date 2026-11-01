package com.github.randir7.notificationservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений notification-service.
 *
 * <p>Компактная версия подхода user-service: каждое исключение
 * преобразуется в ErrorResponse с семантически верным HTTP-статусом.
 * Выбор статусов — часть контракта API, а не деталь реализации.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Провал Bean Validation (@NotBlank, @Email) → 400 с разбивкой по полям.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.putIfAbsent(err.getField(), err.getDefaultMessage()));
        
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Ошибка валидации входных данных",
                request.getRequestURI(),
                fieldErrors));
    }
    
    /**
     * Битый JSON или значение eventType вне enum → 400.
     * Десериализация Jackson падает именно этим исключением.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        log.debug("Некорректное тело запроса: {}", ex.getMessage());
        
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Тело запроса содержит некорректный JSON или неизвестный тип уведомления",
                request.getRequestURI()));
    }
    
    /**
     * Сбой SMTP → 503 Service Unavailable.
     *
     * <p>503 выбран вместо 500 осознанно: недоступность почтового сервера —
     * временное состояние ВНЕШНЕЙ зависимости, а не дефект кода сервиса.
     * Семантика 503 сообщает клиенту «повтори позже», что соответствует
     * реальности и позволяет клиентам с retry-политикой вести себя верно.
     */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<ErrorResponse> handleMailFailure(MailException ex,
                                                           HttpServletRequest request) {
        log.error("Сбой SMTP при отправке уведомления: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "Почтовый сервер недоступен, повторите попытку позже",
                request.getRequestURI()));
    }
    
    /**
     * Страховка: всё остальное → 500 без раскрытия внутренностей клиенту.
     * Полный текст и стектрейс остаются только в логах.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        log.error("Необработанная ошибка при {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Внутренняя ошибка сервера",
                request.getRequestURI()));
    }
}