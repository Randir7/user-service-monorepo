package com.github.randir7.userservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация Springdoc OpenAPI: описание API для Swagger UI и клиентов.
 *
 * <p>Регистрирует бин {@link OpenAPI}, содержащий метаданные проекта.
 * Springdoc подхватывает этот бин автоматически и включает его содержимое
 * в генерируемую OpenAPI 3.0 спецификацию.
 *
 * <p><b>Почему Java-класс, а не YAML:</b> метаданные проекта (название,
 * описание, контакты, лицензия, теги) — это структурированный объект
 * с вложенными полями. Для таких структур Java-код читается лучше,
 * чем YAML с глубокими отступами, а IDE даёт автодополнение и проверку типов.
 *
 * <p><b>Глобальные теги</b> (список {@link Tag}) задаются здесь как справочник:
 * все теги, которые будут использованы в аннотациях {@code @Tag} контроллеров,
 * описываются в одном месте. Это обеспечивает единый стиль описаний
 * и предотвращает дублирование.
 *
 * <p><b>КРИТИЧЕСКИ ВАЖНО:</b> мы жестко задаем URL сервера как адрес API Gateway
 * (http://localhost:8080). Без этого Springdoc автоопределит внутренний IP
 * Docker-контейнера (например, 172.19.0.8:8081), и Swagger UI будет пытаться
 * стучаться напрямую в контейнер, получая ошибки CORS или Network Failure.
 */
@Configuration
public class OpenApiConfig {
    
    /**
     * Бин с метаданными API.
     *
     * <p>{@code @Bean} регистрирует объект в Spring-контейнере.
     * Springdoc находит этот бин по типу {@link OpenAPI} и использует
     * его для заполнения раздела info в генерируемой спецификации.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                // -----------------------------------------------------------
                // servers — список серверов API.
                // Жестко задаем Gateway как единственный сервер.
                // Это критично для работы Swagger UI через Gateway!
                // -----------------------------------------------------------
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("API Gateway")
                ))
                // -----------------------------------------------------------
                // info — главный раздел документации: что это за API.
                // Отображается в верхней части Swagger UI.
                // -----------------------------------------------------------
                .info(new Info()
                        .title("User Service API")
                        .version("1.0.0")
                        .description("""
                                REST API для управления пользователями.
                                
                                ## Возможности
                                - CRUD операции над пользователями
                                - Валидация входных данных (Bean Validation)
                                - Нормализация email (lowercase + trim)
                                - Публикация событий создания/удаления в Kafka
                                
                                ## Формат ошибок
                                Все ошибки возвращаются в едином формате `ErrorResponse`:
                                timestamp, status, error, message, path, errors (для валидации).
                                """)
                        .contact(new Contact()
                                .name("User Service Team")
                                .email("team@example.com")
                                .url("https://github.com/Randir7/user-service-spring-kafka"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                // -----------------------------------------------------------
                // tags — описания групп эндпоинтов (отображаются в Swagger UI
                // как секции). Используются совместно с @Tag(name = "...")
                // на контроллерах.
                // -----------------------------------------------------------
                .tags(List.of(
                        new Tag()
                                .name("Users")
                                .description("Операции управления пользователями: создание, чтение, обновление, удаление")
                ));
    }
}