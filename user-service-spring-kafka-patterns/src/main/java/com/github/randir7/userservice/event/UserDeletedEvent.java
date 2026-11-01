package com.github.randir7.userservice.event;

/**
 * Доменное событие: пользователь удалён.
 *
 * <p>Аналог UserCreatedEvent для операции удаления. Публикуется сервисом
 * внутри транзакции; фактическая отправка в Kafka происходит после коммита.
 *
 * @param userId идентификатор удалённого пользователя
 * @param email  email удалённого пользователя (нормализованный)
 */
public record UserDeletedEvent(Long userId, String email) {
}