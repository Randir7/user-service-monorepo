package com.github.randir7.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Настройка обработки ошибок потребления Kafka.
 *
 * <p>Заменяет поведение DefaultErrorHandler по умолчанию
 * (быстрые повторы и тихий пропуск сообщения) на явную политику:
 *
 * <ol>
 *   <li>Пауза 2 секунды, до 3 повторных попыток обработки сообщения.
 *       Пауза даёт временным сбоям (SMTP-сервер перезапускается, сеть мигает)
 *       шанс исчезнуть без вмешательства человека.</li>
 *   <li>После исчерпания попыток DeadLetterPublishingRecoverer публикует
 *       сообщение в топик "&lt;исходный&gt;.DLT" (user-created.DLT,
 *       user-deleted.DLT), сохраняя исходные заголовки и добавляя
 *       заголовки с причиной сбоя.</li>
 *   <li>Offset исходного сообщения коммитится: потребление продолжается
 *       со следующих сообщений, «ядовитое» сообщение не блокирует очередь.</li>
 * </ol>
 *
 * <p>Сообщения из DLT автоматически НЕ переобрабатываются: возврат их
 * в основной топик — отдельная эксплуатационная операция
 * (ручная или инструментом kafka-consumer-groups / kafka-replay).
 * Для учебной задачи достаточно видеть событие в DLT и понимать причину.
 *
 * <p>KafkaTemplate для восстановителя предоставляется автоконфигурацией
 * spring-kafka: сервис-потребитель одновременно является продюсером
 * исключительно для служебной цели — перекладывания сообщений в DLT.
 */
@Configuration
public class KafkaConsumerConfig {
    
    /** Интервал между повторами обработки, мс. */
    private static final long RETRY_INTERVAL_MS = 2000L;
    
    /** Число повторных попыток после первого сбоя. */
    private static final long RETRY_ATTEMPTS = 3L;
    
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS));
    }
}