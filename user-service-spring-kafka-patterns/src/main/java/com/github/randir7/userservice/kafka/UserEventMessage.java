package com.github.randir7.userservice.kafka;

import java.time.Instant;

/**
 * Тело сообщения Kafka (JSON-контракт между сервисами).
 *
 * <p>Пример JSON в топике:
 * <pre>
 * {"userId":42,"email":"ivan@example.com","occurredAt":"2026-10-19T14:30:00Z"}
 * </pre>
 *
 * <p>Тип операции НЕ хранится в теле: его выражает сам топик
 * (user-created или user-deleted). Потребитель подписывается только
 * на интересные ему топики и не фильтрует сообщения по полю.
 *
 * @param userId     идентификатор пользователя в user-service
 * @param email      адрес получателя уведомления
 * @param occurredAt момент фиксации события (UTC, ISO-8601)
 */
public record UserEventMessage(Long userId, String email, Instant occurredAt) {
}