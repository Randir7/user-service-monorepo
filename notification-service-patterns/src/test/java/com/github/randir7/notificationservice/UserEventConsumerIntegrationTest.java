package com.github.randir7.notificationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.randir7.notificationservice.kafka.UserEventMessage;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест пути «Kafka → UserEventConsumer → NotificationService → SMTP».
 *
 * <p>Проверяется весь асинхронный тракт: сообщение публикуется в реальный
 * брокер (Testcontainers), потребитель группы notification-service читает
 * его из топика и отправляет письмо в GreenMail.
 *
 * <p>В отличие от синхронного REST-пути, здесь результат отсрочен:
 * между публикацией и письмом проходят poll-циклы консьюмера.
 * Awaitility опрашивает ящик с коротким интервалом и завершает ожидание
 * сразу после появления письма — тест не тратит время зря и не падает
 * от произвольно выбранной паузы.
 */
@SpringBootTest(properties = "spring.mail.port=3025")
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",  // Отключение Eureka Client в тестах
        "spring.cloud.config.enabled=false"  // Отключение Config Client в тестах
})
class UserEventConsumerIntegrationTest {
    
    /** Изолированный брокер Kafka; образ совпадает с dev-окружением. */
    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.0.0"));
    
    private static GreenMail greenMail;
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${app.kafka.topic-user-created}")
    private String userCreatedTopic;
    
    @Value("${app.kafka.topic-user-deleted}")
    private String userDeletedTopic;
    
    @BeforeAll
    static void startMailServer() {
        greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();
    }
    
    @AfterAll
    static void stopMailServer() {
        greenMail.stop();
    }
    
    @BeforeEach
    void resetMailbox() {
        greenMail.reset();
    }
    
    @Test
    @DisplayName("Событие user-created → письмо о создании аккаунта")
    void userCreatedEventShouldDeliverCreationEmailToUser() throws Exception {
        publish(userCreatedTopic, "kafka-created@example.com", 101L);
        
        MimeMessage mail = awaitSingleEmail("example.com");
        
        assertThat(mail.getSubject()).isEqualTo("Регистрация на сайте");
        assertThat(mail.getContent().toString())
                .isEqualTo("Здравствуйте! Ваш аккаунт на сайте My Super Site был успешно создан.");
        assertThat(mail.getAllRecipients()[0].toString())
                .isEqualTo("kafka-created@example.com");
    }
    
    @Test
    @DisplayName("Событие user-deleted → письмо об удалении аккаунта")
    void userDeletedEventShouldDeliverDeletionEmailToUser() throws Exception {
        publish(userDeletedTopic, "kafka-deleted@example.com", 202L);
        
        MimeMessage mail = awaitSingleEmail("example.com");
        
        assertThat(mail.getSubject()).isEqualTo("Удаление аккаунта");
        assertThat(mail.getContent().toString())
                .isEqualTo("Здравствуйте! Ваш аккаунт был удалён.");
    }
    
    // ==================== вспомогательные методы ====================
    
    /**
     * Публикация контракта события в указанный топик.
     *
     * <p>get() с таймаутом превращает асинхронную отправку в синхронную
     * контрольную точку: проблема публикации (брокер недоступен, сериализация
     * сломана) проявится сразу и с понятной причиной, а не замаскируется
     * под таймаут ожидания письма.
     */
    private void publish(String topic, String email, long userId) throws Exception {
        UserEventMessage message = new UserEventMessage(userId, email, Instant.now());
        String json = objectMapper.writeValueAsString(message);
        kafkaTemplate.send(topic, email, json).get(10, TimeUnit.SECONDS);
    }
    
    /**
     * Ожидание ровно одного письма в домене и возврат его для проверок.
     *
     * <p>Политика ожидания: до 15 секунд, опрос каждые 200 мс.
     * Успешный сценарий завершается обычно за доли секунды;
     * деградация окружения (холодный старт консьюмера) укладывается в лимит.
     */
    private MimeMessage awaitSingleEmail(String domain) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> greenMail.getReceivedMessagesForDomain(domain).length == 1);
        
        return greenMail.getReceivedMessagesForDomain(domain)[0];
    }
}