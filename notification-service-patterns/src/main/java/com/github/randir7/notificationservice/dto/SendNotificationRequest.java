package com.github.randir7.notificationservice.dto;

import com.github.randir7.notificationservice.service.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO запроса прямой отправки уведомления (POST /api/v1/notifications).
 *
 * <p>Состав полей зеркален данным, приходящим в Kafka-событии
 * (email получателя + тип операции): оба входа предоставляют ядру
 * одинаковый набор сведений, различаясь лишь транспортом.
 *
 * <p>Поле eventType типизировано enum напрямую: Jackson преобразует строки
 * "USER_CREATED" / "USER_DELETED" из JSON в соответствующие константы.
 * Значение вне enum приводит к ошибке десериализации
 * (HttpMessageNotReadableException → 400 Bad Request), поэтому
 * дополнительных валидационных аннотаций на поле не требуется:
 * множество допустимых значений задано самим типом.
 *
 * <p>Ссылка из пакета dto на enum из пакета service направлена
 * «снаружи внутрь» согласно правилу зависимостей: граничный слой
 * (контракты API) может использовать понятия внутренних слоёв,
 * обратное направление было бы ошибкой layering.
 */
public record SendNotificationRequest(
        
        @NotBlank(message = "Адрес получателя не должен быть пустым")
        @Email(message = "Некорректный формат email")
        String email,
        
        @NotNull(message = "Тип уведомления обязателен")
        NotificationType eventType
) {
}