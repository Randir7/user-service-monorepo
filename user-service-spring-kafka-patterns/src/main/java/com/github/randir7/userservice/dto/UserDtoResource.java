package com.github.randir7.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

/**
 * Ресурс пользователя с гипермедиа-ссылками (HATEOAS).
 *
 * <p>Наследуется от {@link RepresentationModel}, который добавляет поле _links
 * для хранения ссылок на связанные ресурсы. Это стандарт HAL (Hypertext
 * Application Language) — формат гипермедиа для REST API.
 *
 * <p>Отличие от UserDto: UserDto — чистая передача данных (record),
 * UserDtoResource — представление данных с навигацией (класс).
 * Такое разделение позволяет:
 * <ul>
 *   <li>Сохранить UserDto неизменяемым и простым для сериализации</li>
 *   <li>Добавлять ссылки только там, где они нужны (в контроллере)</li>
 *   <li>Легко тестировать бизнес-логику без зависимостей от HATEOAS</li>
 * </ul>
 *
 * <p>При сериализации Jackson выводит все поля класса + поле _links
 * из RepresentationModel. Старые тесты, проверяющие только основные поля,
 * продолжают работать.
 */
@Schema(description = "Пользователь с гипермедиа-ссылками для навигации")
public class UserDtoResource extends RepresentationModel<UserDtoResource> {
    
    @Schema(description = "Уникальный идентификатор пользователя", example = "1")
    private Long id;
    
    @Schema(description = "Имя пользователя", example = "Иван Петров")
    private String name;
    
    @Schema(description = "Email адрес", example = "ivan@example.com")
    private String email;
    
    @Schema(description = "Возраст пользователя", example = "25")
    private Integer age;
    
    @Schema(description = "Дата и время создания аккаунта", example = "2026-01-10T12:00:00")
    private LocalDateTime createdAt;
    
    // Конструктор по умолчанию для Jackson
    public UserDtoResource() {
    }
    
    // Конструктор с параметрами для ручного создания
    public UserDtoResource(Long id, String name, String email, Integer age, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.age = age;
        this.createdAt = createdAt;
    }
    
    // Геттеры (сеттеры не нужны — объект заполняется через конструктор или Jackson)
    public Long getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public Integer getAge() {
        return age;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}