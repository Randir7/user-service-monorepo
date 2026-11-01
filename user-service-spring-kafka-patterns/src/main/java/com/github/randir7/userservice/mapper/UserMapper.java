package com.github.randir7.userservice.mapper;

import com.github.randir7.userservice.dto.CreateUserRequest;
import com.github.randir7.userservice.dto.UpdateUserRequest;
import com.github.randir7.userservice.dto.UserDto;
import com.github.randir7.userservice.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Маппер: единственное место в проекте, где entity превращается в DTO и обратно.
 *
 * <p><b>@Component</b> — аннотация Spring: при старте приложения контейнер
 * найдет этот класс (благодаря @ComponentScan из Application), создаст его
 * экземпляр (бин) и будет готов внедрить его туда, где он нужен
 *
 * <p><b>Принцип разделения:</b> маппер — "глупый" конвертер форматов.
 * Он НЕ содержит бизнес-правил. Например, нормализация email (trim,
 * lowerCase) останется в UserService, потому что это БИЗНЕС-решение,
 * а не техническое преобразование.
 */
@Component
public class UserMapper {
    
    /** Entity → DTO ответа. Вызывается перед отправкой данных клиенту. */
    public UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAge(),
                user.getCreatedAt()
        );
    }
    
    /** Список entity → список DTO. Stream API из Java Core в действии. */
    public List<UserDto> toDtoList(List<User> users) {
        return users.stream()
                .map(this::toDto)   // метод-ссылка: каждый User прогоняется через toDto
                .toList();          // и собирается в неизменяемый List (Java 16+)
    }
    
    /** DTO запроса на создание → новый transient-entity (id и createdAt еще null). */
    public User toEntity(CreateUserRequest request) {
        return new User(
                request.name(),    // у record геттеры называются БЕЗ get: name(), email()
                request.email(),
                request.age()
        );
    }
    
    /**
     * DTO запроса на обновление → применение изменений к УЖЕ СУЩЕСТВУЮЩЕМУ entity.
     * Возвращать ничего не нужно: метод изменяет переданный объект,
     * а Hibernate внутри транзакции сам заметит изменения (dirty checking)
     * и выполнит UPDATE при коммите.
     */
    public void updateEntity(User user, UpdateUserRequest request) {
        user.setName(request.name());
        user.setEmail(request.email());
        user.setAge(request.age());
    }
}