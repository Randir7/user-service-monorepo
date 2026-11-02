package com.github.randir7.userservice;

import com.github.randir7.userservice.dto.CreateUserRequest;
import com.github.randir7.userservice.dto.UpdateUserRequest;
import com.github.randir7.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест ВСЕГО API: HTTP → контроллер → сервис → БД → ответ.
 *
 * <p><b>@SpringBootTest(RANDOM_PORT)</b>: поднимает полное приложение
 * со встроенным Tomcat на СЛУЧАЙНОМ свободном порту (параллельные запуски
 * тестов не конфликтуют). TestRestTemplate автоматически знает этот порт.
 *
 * <p><b>@Testcontainers + @Container</b>: расширение JUnit 5, которое
 * стартует Docker-контейнер PostgreSQL ПЕРЕД всеми тестами класса
 * (static-поле = один контейнер на класс, это быстро) и остановит после.
 *
 * <p><b>@ServiceConnection</b> — магия Spring Boot: контейнер сам
 * сообщает приложению свои host/port/credentials, ПЕРЕЗАПИСЫВАЯ
 * spring.datasource.* из application.yml.
 *
 * <p><b>KafkaContainer</b> — изолированный брокер Kafka для тестов.
 * События публикуются в этот тестовый брокер, а не в dev-инфраструктуру.
 *
 * <p>Имена методов декларативны: [сценарий]Should[Ожидание],
 * описание сценария — в @DisplayName.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")  // Использует application.yml из test/resources
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false"
})
class UserApiIntegrationTest {
    
    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.0.0"));
    
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    
    @Autowired
    private TestRestTemplate restTemplate;  // HTTP-клиент для тестов
    
    @Autowired
    private UserRepository userRepository;  // для очистки БД между тестами
    
    @BeforeEach
    void cleanDatabase() {
        // Тесты должны быть НЕЗАВИСИМЫМИ: каждый стартует с чистой БД.
        userRepository.deleteAll();
    }
    
    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
    
    @Test
    @DisplayName("Полный CRUD-цикл с проверкой HATEOAS-ссылок")
    void crudCycleShouldCreateReadUpdateAndDeleteUserWithExpectedStatuses() {
        // 1. CREATE
        // Используем String.class для десериализации, чтобы проверить наличие _links
        // без создания отдельного DTO-класса для UserDtoResource
        ResponseEntity<String> createdResponse = restTemplate.exchange(
                "/api/v1/users", HttpMethod.POST,
                jsonEntity(new CreateUserRequest("Ivan", "Ivan@Example.COM", 28)),
                String.class);
        
        assertThat(createdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdResponse.getHeaders().getLocation()).isNotNull();
        
        String body = createdResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("\"email\":\"ivan@example.com\""); // нормализация
        assertThat(body).contains("\"createdAt\""); // дата должна быть проставлена
        
        // Проверяем наличие HATEOAS-ссылок
        assertThat(body).contains("\"_links\"");
        assertThat(body).contains("\"self\"");
        assertThat(body).contains("\"users\"");
        
        // Извлекаем id из Location header (формат: http://localhost:8080/api/v1/users/1)
        String location = createdResponse.getHeaders().getLocation().toString();
        Long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
        assertThat(id).isNotNull(); // id выдан БД
        
        // 2. READ
        ResponseEntity<String> got = restTemplate.getForEntity(
                "/api/v1/users/" + id, String.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody()).contains("\"name\":\"Ivan\"");
        // Проверяем HATEOAS-ссылки (у getById есть ещё и delete)
        assertThat(got.getBody()).contains("\"_links\"");
        assertThat(got.getBody()).contains("\"self\"");
        assertThat(got.getBody()).contains("\"delete\"");
        
        // 3. UPDATE
        ResponseEntity<String> updated = restTemplate.exchange(
                "/api/v1/users/" + id, HttpMethod.PUT,
                jsonEntity(new UpdateUserRequest("Ivan P.", "ivan.p@example.com", 29)),
                String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).contains("\"name\":\"Ivan P.\"");
        assertThat(updated.getBody()).contains("\"age\":29");
        assertThat(updated.getBody()).contains("\"_links\"");
        
        // 4. DELETE
        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/v1/users/" + id, HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        
        // 5. После удаления — 404
        ResponseEntity<String> gone = restTemplate.getForEntity(
                "/api/v1/users/" + id, String.class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gone.getBody()).contains("\"status\":404");
    }
    
    @Test
    @DisplayName("GET /users: возвращает HAL-коллекцию с _embedded и _links")
    void findAllShouldReturnHalCollection() {
        // Создаём двух пользователей
        restTemplate.exchange("/api/v1/users", HttpMethod.POST,
                jsonEntity(new CreateUserRequest("Ivan", "ivan@example.com", 28)),
                String.class);
        restTemplate.exchange("/api/v1/users", HttpMethod.POST,
                jsonEntity(new CreateUserRequest("Petr", "petr@example.com", 30)),
                String.class);
        
        // Получаем список
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/users", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        String body = response.getBody();
        // Проверяем HAL-структуру: CollectionModel оборачивает ресурсы в _embedded
        assertThat(body).contains("\"_embedded\"");
        // Имя поля генерируется из имени класса UserDtoResource + "List"
        assertThat(body).contains("\"userDtoResourceList\"");
        // Коллекция имеет собственную ссылку self
        assertThat(body).contains("\"_links\"");
        assertThat(body).contains("\"self\"");
        
        // Проверяем наличие обоих пользователей
        assertThat(body).contains("ivan@example.com");
        assertThat(body).contains("petr@example.com");
    }
    
    @Test
    @DisplayName("Дубликат email → 409 Conflict")
    void createWithDuplicateEmailShouldReturn409Conflict() {
        // первый пользователь уже создан
        restTemplate.exchange("/api/v1/users", HttpMethod.POST,
                jsonEntity(new CreateUserRequest("Ivan", "ivan@example.com", 28)),
                String.class);
        
        // второй пользователь с тем же email
        ResponseEntity<String> second = restTemplate.exchange(
                "/api/v1/users", HttpMethod.POST,
                jsonEntity(new CreateUserRequest("Petr", "ivan@example.com", 30)),
                String.class);
        
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("ivan@example.com");
    }
    
    @Test
    @DisplayName("Невалидное тело → 400 с деталями по полям")
    void createWithInvalidBodyShouldReturn400WithErrorsByFields() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.POST,
                jsonEntity(new CreateUserRequest("", "bad-email", -1)),
                String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .contains("\"errors\"")
                .contains("email")
                .contains("age");
    }
    
    @Test
    @DisplayName("GET несуществующего пользователя → 404")
    void getByIdWithNonExistentIdShouldReturn404WithErrorResponse() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/users/424242", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"status\":404");
    }
}