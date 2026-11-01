package com.github.randir7.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO ОТВЕТА API: то, что клиент увидит в JSON.
 *
 * <p> Здесь НЕТ аннотаций валидации — они нужны только
 * на ВХОДЕ. На выходе данные отдаются без проверки.
 *
 * <p>Поле createdAt сериализуется Jackson'ом в ISO-8601 строку,
 * например: "2026-01-10T20:59:22.743" — благодаря настройке
 * write-dates-as-timestamps: false в application.yml.
 * Без нее клиент получил бы нечитаемый массив [2026, 1, 10, 20, 59, 22].
 *
 * <p>Аннотации @Schema улучшают документацию в Swagger UI:
 * добавляют описания полей, примеры значений и ограничения.
 * Без них поля отображались бы только с типами (string, integer).
 */
@Schema(description = "Пользователь системы")
public record UserDto(
        
        @Schema(description = "Уникальный идентификатор пользователя", example = "1")
        Long id,
        
        @Schema(description = "Имя пользователя", example = "Иван Петров", maxLength = 100)
        String name,
        
        @Schema(description = "Email адрес (уникален, нормализован в нижний регистр)",
                example = "ivan@example.com", maxLength = 255)
        String email,
        
        @Schema(description = "Возраст пользователя", example = "25", minimum = "0", maximum = "150")
        Integer age,
        
        @Schema(description = "Дата и время создания аккаунта (UTC)",
                example = "2026-01-10T12:00:00")
        LocalDateTime createdAt
) {
}