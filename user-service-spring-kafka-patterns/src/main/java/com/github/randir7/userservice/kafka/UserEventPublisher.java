package com.github.randir7.userservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.randir7.userservice.event.UserCreatedEvent;
import com.github.randir7.userservice.event.UserDeletedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Мост между доменными событиями приложения и Kafka.
 *
 * <p><b>Роль в архитектуре:</b> сервисный слой не знает о существовании Kafka.
 * Он публикует доменные события (UserCreatedEvent, UserDeletedEvent) через
 * стандартный механизм Spring — ApplicationEventPublisher. Данный компонент
 * подписан на эти события и преобразует их в JSON-сообщения Kafka.
 * Такое разделение оставляет бизнес-код независимым от транспорта.
 *
 * <p><b>Фаза AFTER_COMMIT:</b> методы-слушатели вызываются ТОЛЬКО после
 * успешного коммита транзакции, в которой опубликовано событие.
 * Если транзакция откатилась (например, нарушение уникальности email
 * на уровне БД), слушатель не вызывается вовсе — в Kafka не попадает
 * событие о данных, которых не существует. Это защита от ложных уведомлений.
 *
 * <p><b>Ограничение фазы:</b> при публикации события ВНЕ транзакции слушатель
 * с AFTER_COMMIT не срабатывает (fallbackExecution по умолчанию выключен).
 * Все публикующие методы UserService помечены @Transactional,
 * поэтому условие выполнено.
 *
 * <p><b>Ключ сообщения:</b> в качестве ключа Kafka передаётся email.
 * Брокер хеширует ключ для выбора партиции, поэтому все события одного
 * пользователя попадают в одну партицию. Внутри партиции сообщения
 * упорядочены по времени записи — порядок «создан → удалён» не перепутается
 * даже при нескольких партициях топика.
 *
 * <p><b>Circuit Breaker и Retry:</b> методы защищены паттернами устойчивости
 * из Resilience4j. При недоступности Kafka:
 * <ul>
 *   <li>Retry делает до 3 попыток с интервалом 1 секунда</li>
 *   <li>Если все попытки провалились — Circuit Breaker открывается</li>
 *   <li>Последующие вызовы идут в fallback-метод без попытки реального вызова</li>
 *   <li>Через 10 секунд Circuit Breaker переходит в HALF_OPEN для проверки доступности</li>
 * </ul>
 * Это защищает от каскадных отказов и снижает нагрузку на недоступный брокер.
 */
@Slf4j
@Component
public class UserEventPublisher {
    
    /**
     * Продюсер Kafka, созданный автоконфигурацией spring-kafka
     * на основе свойств spring.kafka.* из application.yml
     * (bootstrap-servers и сериализаторы ключа/значения).
     * Дженерики <String, String>: ключ — email, значение — готовый JSON.
     */
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    /**
     * ObjectMapper, созданный автоконфигурацией Spring Boot.
     * Он учитывает свойства spring.jackson.*: в частности,
     * write-dates-as-timestamps: false, поэтому Instant сериализуется
     * в читаемую ISO-8601 строку, а не в массив чисел.
     */
    private final ObjectMapper objectMapper;
    
    /** Имена топиков берутся из конфигурации, а не из констант кода. */
    private final String userCreatedTopic;
    private final String userDeletedTopic;
    
    public UserEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              @Value("${app.kafka.topic-user-created}") String userCreatedTopic,
                              @Value("${app.kafka.topic-user-deleted}") String userDeletedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.userCreatedTopic = userCreatedTopic;
        this.userDeletedTopic = userDeletedTopic;
    }
    
    /**
     * Реакция на создание пользователя: отправка события в топик user-created.
     * Вызывается инфраструктурой Spring после коммита транзакции создания.
     *
     * <p><b>@CircuitBreaker:</b> оборачивает метод в паттерн Circuit Breaker.
     * При ошибке вызывается fallback-метод {@link #onUserCreatedFallback}.
     * Имя "kafkaPublisher" ссылается на конфигурацию в application.yml.
     *
     * <p><b>@Retry:</b> добавляет повторные попытки (3 раза с интервалом 1 сек)
     * перед тем, как Circuit Breaker зафиксирует ошибку.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "onUserCreatedFallback")
    @Retry(name = "kafkaPublisher")
    public void onUserCreated(UserCreatedEvent event) {
        send(userCreatedTopic, event.userId(), event.email());
    }
    
    /**
     * Реакция на удаление пользователя: отправка события в топик user-deleted.
     * Вызывается инфраструктурой Spring после коммита транзакции удаления.
     *
     * <p>Защищён теми же паттернами устойчивости, что и onUserCreated.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "onUserDeletedFallback")
    @Retry(name = "kafkaPublisher")
    public void onUserDeleted(UserDeletedEvent event) {
        send(userDeletedTopic, event.userId(), event.email());
    }
    
    /**
     * Сериализация контракта в JSON и отправка сообщения в указанный топик.
     *
     * <p><b>Синхронность:</b> вызов {@code .get()} блокирует поток до получения
     * результата от Kafka. Это необходимо для корректной работы Circuit Breaker:
     * исключения должны пробрасываться синхронно, чтобы аннотация @CircuitBreaker
     * могла их перехватить. Без .get() ошибка обработалась бы асинхронно в
     * whenComplete, и Circuit Breaker не увидел бы её.
     *
     * <p><b>Политика ошибок:</b> ошибка доставки пробрасывается как RuntimeException,
     * что позволяет Circuit Breaker зафиксировать её и открыть circuit при
     * достижении порога ошибок (50% из 10 последних вызовов).
     *
     * <p><b>Fallback:</b> при срабатывании Circuit Breaker (circuit открыт) или
     * исчерпании попыток Retry управление передаётся в fallback-методы,
     * которые логируют ошибку без попытки реального вызова.
     *
     * <p><b>Направление развития:</b> гарантированную доставку в production
     * обеспечивает паттерн Transactional Outbox: событие сначала пишется
     * в таблицу БД в той же транзакции, затем отдельный процесс выгружает
     * его в Kafka. Для учебной задачи достаточно логирования ошибок в fallback.
     *
     * @param topic  имя топика назначения (user-created или user-deleted)
     * @param userId идентификатор пользователя из доменного события
     * @param email  email пользователя; используется и как ключ партиционирования,
     *               и как поле контракта для получателя уведомления
     */
    private void send(String topic, Long userId, String email) {
        UserEventMessage message = new UserEventMessage(userId, email, Instant.now());
        try {
            String json = objectMapper.writeValueAsString(message);
            
            // Третий аргумент send — значение сообщения (тело),
            // второй — ключ партиционирования (email).
            // .get() делает вызов синхронным: ждём результата от Kafka.
            // Это позволяет Circuit Breaker перехватывать исключения.
            kafkaTemplate.send(topic, email, json).get();
            
            log.info("Событие доставлено в топик {}: userId={}, email={}, json={}",
                    topic, userId, email, json);
            
        } catch (JsonProcessingException e) {
            // Сериализация record'а с простыми полями практически не может
            // завершиться ошибкой, но метод writeValueAsString объявляет
            // checked-исключение, и контракт требует его обработки.
            log.error("Не удалось сериализовать сообщение для топика {}", topic, e);
            throw new RuntimeException("JSON serialization failed", e);
        } catch (Exception e) {
            // Любая ошибка от Kafka (timeout, broker unavailable, etc.)
            // пробрасывается дальше для обработки Circuit Breaker'ом.
            log.error("Не удалось доставить событие в топик {}: {}", topic, e.getMessage());
            throw new RuntimeException("Kafka publish failed", e);
        }
    }
    
    /**
     * Fallback-метод для onUserCreated при срабатывании Circuit Breaker.
     *
     * <p>Сигнатура fallback должна совпадать с основным методом:
     * те же параметры (UserCreatedEvent) + Throwable в конце для получения
     * информации об ошибке.
     *
     * <p>Вызывается, когда:
     * <ul>
     *   <li>Circuit Breaker открыт (Kafka недоступен, порог ошибок достигнут)</li>
     *   <li>Исчерпаны все попытки Retry (3 попытки провалились)</li>
     * </ul>
     *
     * <p>Fallback логирует ошибку и продолжает работу: пользователь уже
     * сохранён в БД, событие просто не будет отправлено в Kafka.
     * Это graceful degradation: система деградирует, но не падает.
     *
     * @param event событие создания пользователя
     * @param t     исключение, вызвавшее срабатывание fallback
     */
    private void onUserCreatedFallback(UserCreatedEvent event, Throwable t) {
        log.error("Circuit Breaker сработал для события создания пользователя: " +
                        "userId={}, email={}. Событие не отправлено в Kafka. Причина: {}",
                event.userId(), event.email(), t.getMessage());
    }
    
    /**
     * Fallback-метод для onUserDeleted при срабатывании Circuit Breaker.
     *
     * <p>Аналогичен onUserCreatedFallback, но для события удаления.
     *
     * @param event событие удаления пользователя
     * @param t     исключение, вызвавшее срабатывание fallback
     */
    private void onUserDeletedFallback(UserDeletedEvent event, Throwable t) {
        log.error("Circuit Breaker сработал для события удаления пользователя: " +
                        "userId={}, email={}. Событие не отправлено в Kafka. Причина: {}",
                event.userId(), event.email(), t.getMessage());
    }
}