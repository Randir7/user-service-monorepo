package com.github.randir7.notificationservice;

import com.github.randir7.notificationservice.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Точка входа notification-service.
 *
 * <p>@SpringBootApplication включает автоконфигурацию, которая настроит
 * только то, что присутствует в classpath:
 * - spring-boot-starter-web  → Tomcat на порту 8081 и Spring MVC;
 * - spring-kafka             → фабрики продюсера/консьюмера Kafka;
 * - spring-boot-starter-mail → бин JavaMailSender из свойств spring.mail.*.
 *
 * <p>В classpath НЕТ spring-boot-starter-data-jpa, поэтому автоконфигурация
 * БД не активируется вовсе: сервис не создаёт DataSource и не требует
 * PostgreSQL. Это пример stateless-микросервиса: всё состояние живёт
 * вне него (в Kafka и в почтовом сервере).
 */
@SpringBootApplication
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}