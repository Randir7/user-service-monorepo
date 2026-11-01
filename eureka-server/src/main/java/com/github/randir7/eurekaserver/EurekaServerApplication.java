package com.github.randir7.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server — реестр сервисов системы.
 *
 * <p><b>@EnableEurekaServer</b> превращает обычное Spring Boot приложение
 * в сервер реестра: открываются REST-эндпоинты /eureka/apps/* для регистрации
 * инстансов и раздачи таблицы сервисов, а также веб-консоль на корне порта.
 *
 * <p>Сервер stateful в памяти: таблица инстансов живёт в heap и наполняется
 * регистрациями и heartbeat-сигналами клиентов. Перезапуск сервера очищает
 * реестр — клиенты перерегистрируются сами в течение минуты.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}