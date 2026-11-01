package com.github.randir7.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO запроса на СОЗДАНИЕ пользователя (POST /api/v1/users).
 *
 * <p><b>Почему record, а не класс?</b>
 * Record (Java 16+) — идеален для DTO:
 * - неизменяемый (immutable) — поля final, сеттеров нет;
 * - компилятор сам генерирует конструктор, геттеры, equals, hashCode, toString;
 * - Jackson умеет десериализовать JSON в record "из коробки":
 *   поле "name" из JSON попадет в компонент name по имени.
 *
 <p>Аннотации Bean Validation (@NotBlank, @Email, @Min, @Max) проверяют данные
 * на входе. @Schema документирует эти же ограничения в Swagger UI,
 * чтобы клиент API видел правила валидации до отправки запроса.
 */
public record CreateUserRequest(
        
        // @NotBlank = значение не null И после trim() не пустая строка.
        // Т.е. строка "   " НЕ пройдет проверку (в отличие от @NotEmpty).
        @NotBlank(message = "Имя не должно быть пустым")
        @Size(max = 100, message = "Имя не должно превышать 100 символов")
        @Schema(description = "Имя пользователя (пробелы в начале и конце будут удалены)",
                example = "Иван Петров", minLength = 1, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        
        // @Email проверяет базовый формат: наличие @ и точки после нее.
        @NotBlank(message = "Email не должен быть пустым")
        @Email(message = "Некорректный формат email")
        @Size(max = 255, message = "Email не должен превышать 255 символов")
        @Schema(description = "Email адрес (будет нормализован в нижний регистр)",
                example = "ivan@example.com", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED,
                format = "email")
        String email,
        
        // Integer, а не int! Если клиент не пришлет age, Jackson поставит null,
        // и @NotNull сможет это поймать. С примитивом int вместо null
        // автоматически подставился бы 0, и валидация потеряла бы смысл.
        @NotNull(message = "Возраст обязателен для заполнения")
        @Min(value = 0, message = "Возраст не может быть отрицательным")
        @Max(value = 150, message = "Возраст не может превышать 150")
        @Schema(description = "Возраст пользователя в годах",
                example = "25", minimum = "0", maximum = "150", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer age
) {
}