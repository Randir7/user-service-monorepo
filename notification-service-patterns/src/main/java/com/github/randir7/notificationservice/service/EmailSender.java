package com.github.randir7.notificationservice.service;

import com.github.randir7.notificationservice.config.NotificationProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Технический компонент отправки письма через SMTP.
 *
 * <p><b>JavaMailSender</b> создан автоконфигурацией spring-boot-starter-mail
 * на основе свойств spring.mail.* (хост, порт, SMTP-флаги). Ручная работа
 * с jakarta.mail.Session и Properties не требуется.
 *
 * <p><b>MimeMessageHelper с явной кодировкой UTF-8:</b> тексты писем
 * содержат кириллицу. Без явного указания charset кодировка взялась бы
 * из настроек платформы, и на части окружений письма пришли бы
 * с искажёнными символами. Явный UTF-8 устраняет этот класс ошибок.
 *
 * <p><b>SimpleMailMessage против MimeMessage:</b> SimpleMailMessage проще,
 * но поддерживает только plain text без явного управления кодировкой
 * и без вложений. MimeMessageHelper одинаково удобен для plain text
 * и готов к расширению (HTML-тела, вложения) без переписывания кода.
 *
 * <p><b>Синхронность и ошибки:</b> send() блокирует поток до передачи
 * письма SMTP-серверу. Контракт метода неизменен и после добавления
 * паттернов устойчивости: либо письмо передано SMTP-серверу, либо
 * выброшено исключение (MailException), которое вызывающий слой
 * преобразует в свою политику: REST-контроллер вернёт 503,
 * Kafka-потребитель повторит обработку и при исчерпании унесёт
 * сообщение в DLT. Технический компонент не решает бизнес-вопросы повторно.
 *
 * <p><b>Circuit Breaker и Retry:</b> метод send() защищён паттернами
 * устойчивости из Resilience4j:
 * <ul>
 *   <li>Retry делает до 2 попыток с интервалом 2 секунды при временных сбоях
 *       (сеть мигнула, SMTP перезапускается)</li>
 *   <li>При устойчивом сбое (50% ошибок в окне из 5 вызовов) Circuit Breaker
 *       открывается на 30 секунд: новые вызовы НЕ пытаются соединяться
 *       с мёртвым SMTP, а сразу уходят в fallback</li>
 *   <li>Через 30 секунд circuit переходит в HALF_OPEN и пропускает 2 пробных
 *       вызова для проверки восстановления SMTP</li>
 * </ul>
 *
 * <p><b>AOP-ограничение аннотаций:</b> @CircuitBreaker и @Retry работают
 * через прокси Spring AOP, поэтому перехватываются ТОЛЬКО внешние вызовы
 * метода (из других бинов). Вызов this.send(...) внутри этого же класса
 * прошёл бы мимо паттернов. Здесь это не проблема: send() всегда
 * вызывается извне (NotificationService).
 */
@Slf4j
@Component
public class EmailSender {
    
    private final JavaMailSender mailSender;
    private final String fromAddress;
    
    public EmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.fromAddress = properties.from();
    }
    
    /**
     * Отправка plain-text письма одному получателю.
     *
     * <p><b>@CircuitBreaker:</b> оборачивает вызов в паттерн Circuit Breaker
     * с именем "emailSender" (параметры берутся из конфигурации
     * resilience4j.circuitbreaker.instances.emailSender). При открытом
     * circuit управление сразу передаётся в {@link #sendFallback} без
     * попытки соединения со SMTP.
     *
     * <p><b>@Retry:</b> при исключении из основного тела метода Resilience4j
     * повторяет вызов до max-attempts раз с паузой wait-duration.
     * Порядок обёрток: Retry ВНУТРИ Circuit Breaker, то есть каждая попытка
     * Retry учитывается Circuit Breaker'ом как отдельный вызов в окне ошибок.
     *
     * @param to      адрес получателя (email пользователя)
     * @param subject тема письма
     * @param body    тело письма
     */
    @CircuitBreaker(name = "emailSender", fallbackMethod = "sendFallback")
    @Retry(name = "emailSender")
    public void send(String to, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
        } catch (MessagingException e) {
            // Заполнение полей простого письма не может сломаться на практике,
            // но API MimeMessageHelper объявляет checked-исключение.
            throw new IllegalStateException("Не удалось сформировать письмо для " + to, e);
        }
        // mailSender.send() бросает MailException при недоступности SMTP.
        // Исключение НАМЕРЕННО не перехватывается: его увидят Retry (повторит),
        // затем Circuit Breaker (учтёт в окне ошибок) и вызывающий слой
        // (преобразует в 503 или повтор потребления из Kafka).
        mailSender.send(message);
        log.info("Письмо передано SMTP-серверу: to={}, subject={}", to, subject);
    }
    
    /**
     * Fallback при срабатывании Circuit Breaker или исчерпании Retry.
     *
     * <p><b>Сигнатура:</b> параметры основного метода в том же порядке
     * плюс Throwable последним параметром — обязательное требование
     * Resilience4j к fallback-методам. Через Throwable доступна причина
     * сбоя для логирования.
     *
     * <p><b>Политика деградации:</b> в отличие от user-service, где потеря
     * события Kafka допустима (пользователь сохранён, есть лог), здесь
     * письмо — САМА суть операции. Проглотить ошибку означало бы:
     * REST-контроллер вернул бы 200 без отправленного письма, а Kafka-
     * потребитель закоммитил бы offset и потерял уведомление навсегда.
     * Поэтому fallback логирует факт деградации и ПРОБРАСЫВАЕТ MailException:
     * контракт метода («письмо отправлено ИЛИ исключение») сохраняется,
     * а клиент получает честный 503 с возможностью повторить позже.
     *
     * <p><b>Направление развития:</b> в production fallback мог бы сохранять
     * письмо в таблицу отложенной отправки (patern Outbox для почты) и
     * возвращаться без исключения: фоновый процесс дослал бы письма
     * после восстановления SMTP. Для учебной задачи достаточно проброса
     * ошибки с логированием.
     *
     * @param to      адрес получателя (email пользователя)
     * @param subject тема письма
     * @param body    тело письма
     * @param t       причина сбоя: исключение последней попытки Retry
     *                или CallNotPermittedException при открытом circuit
     */
    private void sendFallback(String to, String subject, String body, Throwable t) {
        log.error("Отправка письма деградировала (Circuit Breaker / Retry исчерпан): " +
                        "to={}, subject={}. Письмо НЕ передано SMTP-серверу. Причина: {}",
                to, subject, t.getMessage());
        
        // MailSendException — наследник MailException, поэтому
        // GlobalExceptionHandler преобразует его в 503 Service Unavailable,
        // а Kafka-потребитель уйдёт в повторы и далее в DLT.
        throw new MailSendException("Почтовый сервер недоступен, письмо не отправлено: " + to, t);
    }
}