package com.github.randir7.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway — единая точка входа в систему микросервисов.
 *
 * <p>Класс намеренно пуст: вся логика шлюза декларативна и описана
 * маршрутами в конфигурации (Config Server). Дополнительных аннотаций
 * не требуется: стартер gateway включает роутинг автоматически,
 * стартер eureka-client — регистрацию в реестре.
 *
 * <p>Приложение работает на Netty (WebFlux), а не на Tomcat:
 * spring-cloud-starter-gateway несовместим со spring-boot-starter-web.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}