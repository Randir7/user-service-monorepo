package com.github.randir7.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.randir7.userservice.dto.CreateUserRequest;
import com.github.randir7.userservice.dto.UpdateUserRequest;
import com.github.randir7.userservice.dto.UserDto;
import com.github.randir7.userservice.exception.UserNotFoundException;
import com.github.randir7.userservice.mapper.UserDtoResourceMapper;
import com.github.randir7.userservice.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice-тест веб-слоя.
 *
 * <p><b>@WebMvcTest(UserController.class)</b> поднимает УРЕЗАННЫЙ контекст
 * Spring: только то, что нужно для MVC:
 *   ✅ UserController
 *   ✅ GlobalExceptionHandler (@RestControllerAdvice тоже попадает в slice!)
 *   ✅ Jackson, конвертеры, валидаторы, MockMvc
 *   ❌ НЕ поднимает: UserRepository, UserService, подключение к БД, Tomcat
 *
 * Результат: тест стартует за ~1 секунду вместо ~10.
 *
 * <p><b>@Import(UserDtoResourceMapper.class)</b> добавляет реальный маппер
 * в тестовый контекст. Это проще, чем мокать его, потому что маппер —
 * чистая функция без зависимостей. Контроллер сам вызывает маппер
 * для преобразования UserDto → UserDtoResource с HATEOAS-ссылками.
 *
 * <p>Поскольку настоящего UserService в контексте НЕТ, а контроллеру он
 * нужен — передается мок через @MockitoBean.
 *
 * <p>Имена методов декларативны: [метод][WithУсловие]Should[Ожидание],
 * описание сценария — в @DisplayName.
 */

@WebMvcTest(UserController.class)
@Import(UserDtoResourceMapper.class)
@ActiveProfiles("test")  // Использует application.yml из test/resources
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false"
})
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;           // «виртуальный» HTTP-клиент для тестов
    
    @Autowired
    private ObjectMapper objectMapper; // настоящий Jackson из контекста slice
    
    @MockitoBean
    private UserService userService;   // мок сервиса: БД не участвует
    
    // Вспомогательные фабрики тестовых данных — убирают дублирование в тестах
    private CreateUserRequest validRequest() {
        return new CreateUserRequest("Ivan", "ivan@example.com", 28);
    }
    
    private UserDto sampleDto() {
        return new UserDto(1L, "Ivan", "ivan@example.com", 28,
                LocalDateTime.of(2026, 1, 10, 12, 0));
    }
    
    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
    
    // ==================== POST ====================
    
    @Test
    @DisplayName("POST /users: валидный запрос → 201 + Location + _links")
    void createShouldReturn201WithLocationHeaderAndLinks() throws Exception {
        
        when(userService.create(any(CreateUserRequest.class))).thenReturn(sampleDto());
        
        
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                // Location собирается из ТЕКУЩЕГО запроса; в MockMvc хост — localhost
                .andExpect(header().string("Location", "http://localhost/api/v1/users/1"))
                // Проверка основных полей
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ivan"))
                .andExpect(jsonPath("$.email").value("ivan@example.com"))
                // Проверка HATEOAS-ссылки (добавлены контроллером через UserDtoResource)
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/v1/users/1"))
                .andExpect(jsonPath("$._links.users.href").value("http://localhost/api/v1/users"));
        
        verify(userService).create(any(CreateUserRequest.class));
    }
    
    @Test
    @DisplayName("POST /users: невалидные поля → 400 с разбивкой ошибок по полям")
    void createWithInvalidFieldsShouldReturn400WithErrorsByFields() throws Exception {
        
        CreateUserRequest bad = new CreateUserRequest("", "not-an-email", -5);
        
        
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                // $.errors.<поле> — путь к детали ошибки в ErrorResponse
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.age").exists());
        
        // Сервис не должен даже вызываться: валидация отсекла запрос раньше
        verifyNoInteractions(userService);
    }
    
    @Test
    @DisplayName("POST /users: тело не может быть разобрано в CreateUserRequest → 400")
    void createWithUnparseableBodyShouldReturn400WithReadableMessage() throws Exception {
        // JSON синтаксически ВАЛИДЕН (инспектор IDE молчит), но тип поля age —
        // строка вместо числа. Jackson бросит InvalidFormatException,
        // Spring обернет его в HttpMessageNotReadableException,
        // и GlobalExceptionHandler вернет 400 с понятным сообщением.
        // Текст-блок (Java 15+) избавляет от экранирования кавычек.
        String wrongTypeJson = """
            {"name": "Ivan", "email": "ivan@example.com", "age": "twenty-eight"}
            """;
        
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongTypeJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Тело запроса содержит некорректный JSON"));
    }
    
    // ==================== GET ====================
    
    @Test
    @DisplayName("GET /users/{id}: найден → 200 + DTO + _links")
    void getByIdShouldReturn200WithUserDtoAndLinks() throws Exception {
        
        when(userService.getById(1L)).thenReturn(sampleDto());
        
        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                // LocalDateTime сериализуется в строку
                .andExpect(jsonPath("$.createdAt").value("2026-01-10T12:00:00"))
                // Проверка HATEOAS-ссылки
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/v1/users/1"))
                .andExpect(jsonPath("$._links.users.href").value("http://localhost/api/v1/users"))
                .andExpect(jsonPath("$._links.delete.href").value("http://localhost/api/v1/users/1"));
    }
    
    @Test
    @DisplayName("GET /users/{id}: не найден → 404 в формате ErrorResponse")
    void getByIdWithNonExistentIdShouldReturn404InErrorResponseFormat() throws Exception {
        // сервис бросает доменное исключение
        when(userService.getById(99L)).thenThrow(new UserNotFoundException(99L));
        
        // клиент должен увидеть 404 от GlobalExceptionHandler
        mockMvc.perform(get("/api/v1/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/users/99"));
    }
    
    @Test
    @DisplayName("GET /users/{id}: буквы вместо числа → 400")
    void getByIdWithNonNumericIdShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    @DisplayName("GET /users: список → 200 + HAL-коллекция с _embedded и _links")
    void findAllShouldReturn200WithHalCollection() throws Exception {
        when(userService.findAll()).thenReturn(List.of(
                sampleDto(),
                new UserDto(2L, "Petr", "petr@example.com", 30,
                        LocalDateTime.of(2026, 1, 11, 12, 0))
        ));
        
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                // HAL-структура: _embedded содержит массив ресурсов
                // Имя поля "userDtoResourceList" генерируется из имени класса
                // UserDtoResource + "List" (стандарт Spring HATEOAS)
                .andExpect(jsonPath("$._embedded.userDtoResourceList", hasSize(2)))
                .andExpect(jsonPath("$._embedded.userDtoResourceList[0].id").value(1))
                .andExpect(jsonPath("$._embedded.userDtoResourceList[1].name").value("Petr"))
                // Каждый элемент имеет ссылку self
                .andExpect(jsonPath("$._embedded.userDtoResourceList[0]._links.self.href")
                        .value("http://localhost/api/v1/users/1"))
                // Коллекция имеет ссылку self
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/v1/users"));
    }
    
    // ==================== PUT ====================
    
    @Test
    @DisplayName("PUT /users/{id}: успешное обновление → 200 + обновленный DTO + _links")
    void updateShouldReturn200WithUpdatedDtoAndLinks() throws Exception {
        UserDto updated = new UserDto(1L, "Ivan P.", "ivan.p@example.com", 29,
                LocalDateTime.of(2026, 1, 10, 12, 0));
        when(userService.update(anyLong(), any(UpdateUserRequest.class))).thenReturn(updated);
        
        mockMvc.perform(put("/api/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateUserRequest("Ivan P.", "ivan.p@example.com", 29))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ivan P."))
                .andExpect(jsonPath("$.age").value(29))
                // Проверка HATEOAS-ссылки
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/v1/users/1"))
                .andExpect(jsonPath("$._links.users.href").value("http://localhost/api/v1/users"));
    }
    
    // ==================== DELETE ====================
    
    @Test
    @DisplayName("DELETE /users/{id}: успех → 204 без тела")
    void deleteShouldReturn204AndCallServiceDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1"))
                .andExpect(status().isNoContent());
        
        verify(userService).delete(1L);
    }
    
    @Test
    @DisplayName("DELETE /users/{id}: не найден → 404")
    void deleteWithNonExistentIdShouldReturn404() throws Exception {
        // doThrow — вариант stubbing для void-методов
        // (when(...).thenThrow(...) для void не подходит: нечего оборачивать)
        doThrow(new UserNotFoundException(99L)).when(userService).delete(99L);
        
        mockMvc.perform(delete("/api/v1/users/99"))
                .andExpect(status().isNotFound());
    }
}