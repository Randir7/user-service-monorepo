package com.github.randir7.notificationservice.controller;

import com.github.randir7.notificationservice.dto.SendNotificationRequest;
import com.github.randir7.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-вход прямой отправки почтовых уведомлений.
 *
 * <p>Выполняет требование задачи об отдельном API с функционалом, близким
 * к Kafka-сценарию: вызов делегируется тому же NotificationService,
 * который использует потребитель событий. Общая логика существует
 * в единственном экземпляре.
 *
 * <p><b>Выбор статуса 200 OK:</b> отправка синхронна — к моменту возврата
 * ответа письмо уже передано SMTP-серверу, и клиент получает честный
 * ответ «выполнено». Статус 202 Accepted означал бы «принято, обработается
 * позже» и подошёл бы при наличии асинхронной очереди между контроллером
 * и отправителем; здесь такой очереди нет.
 *
 * <p><b>Тонкий контроллер:</b> ни формирования текстов, ни работы с почтой.
 * Единственная обязанность — распаковать HTTP-запрос, вызвать сервис,
 * упаковать статус. Валидация выполняется декларативно через @Valid
 * до входа в метод.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    
    private final NotificationService notificationService;
    
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    
    /**
     * Отправка уведомления указанного типа на указанный адрес.
     * Успех — 200 без тела: результат операции бинарен
     * (письмо передано SMTP-серверу либо ответ содержит ошибку).
     */
    @PostMapping
    public ResponseEntity<Void> send(@Valid @RequestBody SendNotificationRequest request) {
        notificationService.sendNotification(request.email(), request.eventType());
        return ResponseEntity.ok().build();
    }
}