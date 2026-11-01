package com.github.randir7.notificationservice;

import com.github.randir7.notificationservice.dto.SendNotificationRequest;
import com.github.randir7.notificationservice.service.NotificationType;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест пути «REST API → NotificationService → SMTP».
 *
 * <p>Поднимается полное приложение со случайным HTTP-портом; почтой служит
 * GreenMail на тестовом порту 3025 вместо dev-Mailpit на 1025.
 *
 * <p>Kafka-слушатели отключены свойством auto-startup: данному пути брокер
 * не нужен, а контейнеры слушателей без брокера бесконечно повторяли бы
 * попытки подключения, замедляя тест и засоряя лог.
 *
 * <p>Отправка по этому пути синхронна: к моменту возврата HTTP-ответа
 * письмо уже передано SMTP-серверу, поэтому Awaitility не требуется —
 * достаточно прочитать ящик GreenMail после запроса.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.mail.port=3025",
                "spring.kafka.listener.auto-startup=false"
        }
)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",  // Отключение Eureka Client в тестах
        "spring.cloud.config.enabled=false"  // Отключение Config Client в тестах
})
class NotificationApiIntegrationTest {
    
    /** SMTP-сервер теста; живёт на протяжении всего класса. */
    private static GreenMail greenMail;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @BeforeAll
    static void startMailServer() {
        // ServerSetupTest.SMTP — преднастроенный тестовый SMTP на порту 3025.
        greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();
    }
    
    @AfterAll
    static void stopMailServer() {
        greenMail.stop();
    }
    
    @BeforeEach
    void resetMailbox() {
        // Изоляция тестов: каждый стартует с пустым ящиком,
        // иначе письма предыдущих тестов попадали бы в проверки следующих.
        greenMail.reset();
    }
    
    @Test
    @DisplayName("POST /notifications: тип CREATED → 200 и письмо о создании аккаунта")
    void postWithCreatedTypeShouldReturn200AndDeliverCreationEmail() throws Exception {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/notifications",
                new SendNotificationRequest("api-created@example.com", NotificationType.USER_CREATED),
                Void.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        MimeMessage[] mails = greenMail.getReceivedMessagesForDomain("example.com");
        assertThat(mails).hasSize(1);
        assertThat(mails[0].getSubject()).isEqualTo("Регистрация на сайте");
        assertThat(mails[0].getContent().toString())
                .isEqualTo("Здравствуйте! Ваш аккаунт на сайте My Super Site был успешно создан.");
        assertThat(mails[0].getAllRecipients()[0].toString())
                .isEqualTo("api-created@example.com");
    }
    
    @Test
    @DisplayName("POST /notifications: тип DELETED → 200 и письмо об удалении аккаунта")
    void postWithDeletedTypeShouldReturn200AndDeliverDeletionEmail() throws Exception {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/notifications",
                new SendNotificationRequest("api-deleted@example.com", NotificationType.USER_DELETED),
                Void.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        MimeMessage[] mails = greenMail.getReceivedMessagesForDomain("example.com");
        assertThat(mails).hasSize(1);
        assertThat(mails[0].getSubject()).isEqualTo("Удаление аккаунта");
        assertThat(mails[0].getContent().toString())
                .isEqualTo("Здравствуйте! Ваш аккаунт был удалён.");
    }
    
    @Test
    @DisplayName("POST /notifications: невалидный email → 400 и письмо НЕ отправлено")
    void postWithInvalidEmailShouldReturn400AndDeliverNothing() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/notifications",
                new SendNotificationRequest("not-an-email", NotificationType.USER_CREATED),
                String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // Отрицательная проверка не менее важна позитивной:
        // провалившая валидацию операция не должна оставлять побочных эффектов.
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}