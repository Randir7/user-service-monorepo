package com.github.randir7.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO запроса на ПОЛНОЕ обновление пользователя (PUT /api/v1/users/{id}).
 *
 * <p>PUT по стандарту HTTP означает "полная замена ресурса", поэтому клиент
 * обязан прислать ВСЕ поля.
 * Если был бы PATCH (частичное обновление),то понадобился бы отдельный
 * DTO с полями-обертками или Optional
 */
public record UpdateUserRequest(
        
        @NotBlank(message = "Имя не должно быть пустым")
        @Size(max = 100, message = "Имя не должно превышать 100 символов")
        @Schema(description = "Новое имя пользователя",
                example = "Иван П.", minLength = 1, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        
        @NotBlank(message = "Email не должен быть пустым")
        @Email(message = "Некорректный формат email")
        @Size(max = 255, message = "Email не должен превышать 255 символов")
        @Schema(description = "Новый email адрес (должен быть уникальным)",
                example = "ivan.p@example.com", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED,
                format = "email")
        String email,
        
        @NotNull(message = "Возраст обязателен для заполнения")
        @Min(value = 0, message = "Возраст не может быть отрицательным")
        @Max(value = 150, message = "Возраст не может превышать 150")
        @Schema(description = "Новый возраст пользователя",
                example = "26", minimum = "0", maximum = "150", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer age
) {
}