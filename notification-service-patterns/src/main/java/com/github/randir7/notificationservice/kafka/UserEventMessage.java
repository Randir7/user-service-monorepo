package com.github.randir7.notificationservice.kafka;

import java.time.Instant;

/**
 * Тело сообщения Kafka (JSON-контракт между сервисами).
 *
 * <p>ПОЛНЫЙ ДУБЛИКАТ record'а из user-service (пакет ...userservice.kafka).
 * Дублирование контракта — осознанный компромисс учебной задачи:
 * альтернативами были бы общая maven-библиотека-модуль или реестр схем
 * (Avro / JSON Schema), что избыточно для двух сервисов.
 *
 * <p>Правило сопровождения: любое изменение состава полей выполняется
 * ОДНОВРЕМЕННО в обоих сервисах, иначе десериализация начнёт терять данные
 * (неизвестные поля JSON игнорируются только при соответствующей настройке
 * ObjectMapper, отсутствующие — приходят как null).
 *
 * <p>Пример входящего JSON:
 * <pre>
 * {"userId":42,"email":"ivan@example.com","occurredAt":"2026-10-19T14:30:00Z"}
 * </pre>
 *
 * <p>Тип операции не хранится в теле: его выражает сам топик
 * (user-created или user-deleted). Потребитель подписан на оба топика
 * отдельными методами и знает тип события по факту приёма из топика.
 *
 * @param userId     идентификатор пользователя в user-service
 * @param email      адрес получателя уведомления
 * @param occurredAt момент фиксации события (UTC, ISO-8601)
 */
public record UserEventMessage(Long userId, String email, Instant occurredAt) {
}