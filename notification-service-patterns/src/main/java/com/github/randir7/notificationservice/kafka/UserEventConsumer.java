package com.github.randir7.notificationservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.randir7.notificationservice.service.NotificationService;
import com.github.randir7.notificationservice.service.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Потребитель событий пользовательского жизненного цикла из Kafka.
 *
 * <p><b>Два топика — два метода-слушателя.</b> Альтернативой был бы один
 * метод с массивом topics = {"user-created", "user-deleted"} и определением
 * источника через заголовок KafkaHeaders.RECEIVED_TOPIC. Раздельные методы
 * выбраны как более читаемый и типобезопасный вариант: маршрут
 * «топик → тип уведомления» виден прямо в сигнатурах, без ветвлений в теле.
 *
 * <p><b>Группа консьюмеров</b> в @KafkaListener не указана явно:
 * при отсутствии атрибута groupId используется значение
 * spring.kafka.consumer.group-id из application.yml
 * (notification-service). Все слушатели сервиса читают в одной группе,
 * и брокер хранит для неё единую позицию по партициям.
 *
 * <p><b>Значение приходит строкой:</b> десериализатор значения настроен
 * как StringDeserializer, поэтому метод принимает сырой JSON-текст
 * и разбирает его ObjectMapper'ом вручную. Это цена язык-независимого
 * контракта: в сообщении нет Spring-специфичных заголовков spring_json_*,
 * и потребителем мог бы быть сервис на любом языке.
 *
 * <p><b>Поток выполнения:</b> методы вызываются потоком listener-контейнера
 * Kafka, а не HTTP-потоком. Обработка порции сообщений идёт последовательно
 * в одном потоке контейнера (concurrency = 1 по умолчанию), что сохраняет
 * порядок событий внутри партиции.
 */
@Slf4j
@Component
public class UserEventConsumer {
    
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    
    public UserEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }
    
    /**
     * Обработка события создания пользователя: письмо «аккаунт создан».
     * Имя топика берётся из конфигурации через placeholder свойства.
     */
    @KafkaListener(topics = "${app.kafka.topic-user-created}")
    public void onUserCreated(String payload) {
        handle(payload, NotificationType.USER_CREATED);
    }
    
    /**
     * Обработка события удаления пользователя: письмо «аккаунт удалён».
     */
    @KafkaListener(topics = "${app.kafka.topic-user-deleted}")
    public void onUserDeleted(String payload) {
        handle(payload, NotificationType.USER_DELETED);
    }
    
    /**
     * Разбор контракта и передача управления единой точке отправки.
     *
     * <p>Исключения десериализации и отправки НАМЕРЕННО не перехватываются:
     * они уходят в обработчик ошибок контейнера Kafka (см. KafkaConsumerConfig),
     * который выполняет повторы и, при исчерпании, уносит сообщение в DLT.
     * Проглатывание ошибки здесь привело бы к тихой потере уведомления
     * и коммиту offset как будто всё успешно.
     */
    private void handle(String payload, NotificationType type) {
        log.info("Получено событие из топика для {}: {}", type, payload);
        
        UserEventMessage event = parse(payload);
        notificationService.sendNotification(event.email(), type);
        
        log.info("Уведомление {} обработано: email={}", type, event.email());
    }
    
    /**
     * Десериализация JSON-контракта в record.
     *
     * <p>Ошибка разбора (битый JSON, несовместимая схема) превращается
     * в IllegalStateException: такое сообщение не станет «лучше» от повторов,
     * но попадёт в DLT после исчерпания попыток и останется видимым
     * для разбора вместо тихого пропуска.
     */
    private UserEventMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, UserEventMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось разобрать сообщение события: " + payload, e);
        }
    }
}