package com.github.randir7.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config Server — централизованное хранилище конфигураций.
 *
 * <p><b>@EnableConfigServer</b> включает режим сервера конфигураций:
 * приложение начинает раздавать конфиги через REST API.
 *
 * <p>Используется <b>native profile</b> (файловая система) вместо git:
 * конфиги лежат в src/main/resources/config/ и раздаются напрямую.
 * Это упрощение для учебного проекта; в production обычно git-бэкенд.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}