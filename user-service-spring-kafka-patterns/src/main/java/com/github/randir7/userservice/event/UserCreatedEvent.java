package com.github.randir7.userservice.event;

/**
 * Доменное событие: пользователь создан.
 *
 * <p>Это ВНУТРИПРОЦЕССНОЕ событие Spring (application event), а не сообщение
 * Kafka. Сервисный слой публикует его через ApplicationEventPublisher,
 * а компонент-слушатель (UserEventPublisher) преобразует его в сообщение
 * Kafka после коммита транзакции.
 *
 * <p>Record выбран осознанно: событие неизменяемо по смыслу —
 * произошедший факт нельзя «подправить».
 *
 * @param userId идентификатор созданного пользователя
 * @param email  email созданного пользователя (нормализованный)
 */
public record UserCreatedEvent(Long userId, String email) {
}