package com.github.randir7.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс приложения — точка входа для Spring Boot.
 *
 * Аннотация @SpringBootApplication — это "комбо" из трех аннотаций:
 *
 * 1. @Configuration:
 *    Говорит Spring, что этот класс может содержать bean-определения
 *    (методы с аннотацией @Bean).
 *
 * 2. @EnableAutoConfiguration:
 *    Магическая часть: Spring Boot смотрит на зависимости в pom.xml
 *    и АВТОМАТИЧЕСКИ настраивает всё, что нужно:
 *
 *    - Видит spring-boot-starter-web? → Настраивает Tomcat и Spring MVC
 *    - Видит spring-boot-starter-data-jpa? → Создает DataSource,
 *      EntityManagerFactory, TransactionManager
 *    - Видит postgresql? → Настраивает JDBC-драйвер
 *    - Видит application.yml? → Читает настройки подключения к БД
 *
 *
 * 3. @ComponentScan:
 *    Говорит Spring сканировать текущий пакет (com.github.randir7.userservice)
 *    и все подпакеты в поисках классов с аннотациями:
 *    - @Component, @Service, @Repository, @Controller, @RestController
 *
 *    Все найденные классы автоматически регистрируются как Spring Beans
 *    (объекты, управляемые Spring IoC контейнером).
 *
 * ВАЖНО: Класс должен быть в корневом пакете (com.github.randir7.userservice),
 * чтобы @ComponentScan нашел все остальные компоненты!
 */
@SpringBootApplication
public class Application {
    
    /**
     * Метод main — стандартная точка входа Java-приложения.
     *
     * SpringApplication.run() делает следующее:
     * 1. Создает Spring Application Context (IoC контейнер)
     * 2. Запускает автоконфигурацию (создает все нужные beans)
     * 3. Запускает встроенный Tomcat на порту 8080
     * 4. Разворачивает REST API
     * 5. Подключается к PostgreSQL (если настроено)
     *
     * После этого приложение готово принимать HTTP-запросы!
     *
     * @param args аргументы командной строки (можно передавать настройки)
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}