package com.github.randir7.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Типобезопасная группа свойств с префиксом app.notification.
 *
 * <p>@ConfigurationProperties связывает record со свойствами из application.yml
 * посредством relaxed binding:
 *   app.notification.site-name → компонент siteName
 *   app.notification.from      → компонент from
 * Kebab-case в YAML и camelCase в коде эквивалентны для Spring Boot.
 *
 * <p>Почему @ConfigurationProperties, а не @Value:
 * - свойства сгруппированы в одном неизменяемом объекте, а не размазаны
 *   по строковым ключам в каждом конструкторе;
 * - поддерживается валидация аннотациями Bean Validation (@NotBlank и т.п.);
 * - IDE подсказывает ключи и ловит опечатки благодаря метаданным;
 * - объект тривиально подменить в тестах, создав record вручную.
 *
 * <p>record гарантирует неизменяемость конфигурации после старта:
 * значения фиксируются один раз при constructor binding.
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(String siteName, String from) {
}