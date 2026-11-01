package com.github.randir7.notificationservice.service;

import com.github.randir7.notificationservice.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Единая точка формирования и отправки почтовых уведомлений.
 *
 * <p>Именно этот сервис будут вызывать оба «входа»:
 * <ul>
 *   <li>Kafka-потребитель событий user-created / user-deleted;
 *   <li>REST-контроллер прямой отправки.
 * </ul>
 *
 * <p>Компонент не знает о Kafka и о HTTP — он оперирует доменными
 * понятиями «email» и «тип уведомления». Это сохраняет его тестируемым
 * без инфраструктуры.
 */
@Slf4j
@Service
public class NotificationService {
    
    private final EmailSender emailSender;
    private final String siteName;
    
    public NotificationService(EmailSender emailSender, NotificationProperties properties) {
        this.emailSender = emailSender;
        this.siteName = properties.siteName();
    }
    
    /**
     * Формирование и отправка уведомления заданного типа.
     *
     * @param email адрес получателя
     * @param type  тип уведомления, определяющий тему и шаблон тела
     */
    public void sendNotification(String email, NotificationType type) {
        String subject = type.getSubject();
        String body = type.renderMessage(siteName);
        log.debug("Сформировано уведомление: type={}, to={}, subject={}", type, email, subject);
        emailSender.send(email, subject, body);
    }
}