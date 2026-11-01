package com.github.randir7.userservice.service;

import com.github.randir7.userservice.dto.CreateUserRequest;
import com.github.randir7.userservice.dto.UpdateUserRequest;
import com.github.randir7.userservice.dto.UserDto;
import com.github.randir7.userservice.entity.User;
import com.github.randir7.userservice.event.UserCreatedEvent;
import com.github.randir7.userservice.event.UserDeletedEvent;
import com.github.randir7.userservice.exception.UserNotFoundException;
import com.github.randir7.userservice.exception.ValidationException;
import com.github.randir7.userservice.mapper.UserMapper;
import com.github.randir7.userservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервисный слой: бизнес-логика работы с пользователями.
 *
 * <p><b>@Service</b> — стереотип Spring (разновидность @Component).
 * Функционально он равен @Component, но семантически помечает класс
 * как слой бизнес-логики. Плюс: на @Service-бины автоматически
 * настраивается трансляция исключений persistence-слоя.
 *
 * <p><b>Dependency Injection через конструктор:</b>
 * Spring сам создаст UserRepository (прокси Spring Data) и UserMapper
 * и передаст их в конструктор при создании бина UserService.
 * Это рекомендуемый способ инъекции (в отличие от @Autowired на поле):
 * - зависимости final → неизменяемы;
 * - класс нельзя создать без зависимостей → нет NPE;
 * - легко тестировать: в юнит-тестах передадим моки вручную.
 *
 * <p><b>Границы ответственности после перехода на Spring:</b>
 * - Форматная валидация (имя не пустое, email похож на email, возраст 0..150)
 *   ТЕПЕРЬ живет в DTO-аннотациях и срабатывает на границе контроллера (@Valid).
 * - БИЗНЕС-правила (уникальность email, нормализация) остаются ЗДЕСЬ,
 *   потому что требуют обращения к БД и знаний о домене.
 *
 * <p><b>Слой возвращает DTO, а не entity:</b> сервис — последний слой,
 * который знает о существовании User как таблицы. Наружу смотрят только UserDto.
 */
@Slf4j   // Lombok: генерирует поле `private static final Logger log = ...`
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    
    public UserService(UserRepository userRepository,
                       UserMapper userMapper,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.eventPublisher = eventPublisher;
    }
    
    // ================= CREATE =================
    
    /**
     * Создает пользователя.
     *
     * <p>@Transactional: прокси откроет транзакцию до метода и сделает
     * commit после. Если checkEmailFree или save бросят исключение —
     * будет rollback, и в БД не останется "полузаписей".
     */
    @Transactional
    public UserDto create(CreateUserRequest request) {
        // Нормализация — бизнес-правило: храним email в нижнем регистре,
        // чтобы "Ivan@Mail.RU" и "ivan@mail.ru" считались одним адресом.
        String name = request.name().trim();
        String email = normalizeEmail(request.email());
        
        checkEmailFree(email);
        
        // record неизменяем, поэтому создаем НОРМАЛИЗОВАННУЮ КОПИЮ запроса
        // и уже ее отдаем мапперу. Идиоматичный прием для record-DTO.
        CreateUserRequest normalized = new CreateUserRequest(name, email, request.age());
        User saved = userRepository.save(userMapper.toEntity(normalized));
        
        // Публикация доменного события. Сам вызов лишь регистрирует событие
        // в синхронизаторе транзакции; реальная отправка в Kafka произойдёт
        // в UserEventPublisher ПОСЛЕ коммита этой транзакции.
        eventPublisher.publishEvent(new UserCreatedEvent(saved.getId(), saved.getEmail()));
        
        log.info("Создан пользователь id={}, email={}", saved.getId(), saved.getEmail());
        return userMapper.toDto(saved);
    }
    
    // ================= READ =================
    
    /**
     * Получить пользователя по id.
     *
     * <p>readOnly = true: транзакция только для чтения (оптимизация).
     * <p>Optional + orElseThrow: идиоматичная замена if (user == null) throw ...
     */
    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toDto)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
    
    /**
     * Список всех пользователей, отсортированный по id.
     *
     * <p>Sort.by("id") — имя ПОЛЯ entity, а не колонки БД (как и в HQL).
     * Старый DAO делал то же самое через "order by u.id" в HQL.
     */
    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userMapper.toDtoList(
                userRepository.findAll(Sort.by("id"))
        );
    }
    
    // ================= UPDATE =================
    
    /**
     * Полное обновление пользователя.
     *
     * <p><b>ГЛАВНЫЙ УРОК HIBERNATE В ЭТОМ МЕТОДЕ — dirty checking:</b>
     * entity, загруженный через findById ВНУТРИ транзакции, находится
     * в состоянии managed: Hibernate держит его в persistence context
     * и сверяет поля с оригиналом. При commit он сам сгенерирует UPDATE
     * по изменившимся полям. Поэтому здесь НЕТ вызова save() — он не нужен!
     * (Вызвать save() на managed-сущности не ошибка, но бессмысленно.)
     */
    @Transactional
    public UserDto update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        
        String name = request.name().trim();
        String email = normalizeEmail(request.email());
        Integer age = request.age();
        checkEmailNotOccupiedByAnother(email, id);
        
        UpdateUserRequest normalized = new UpdateUserRequest(name, email, age);
        userMapper.updateEntity(user, normalized);
        
        log.info("Обновлен пользователь id={}", id);
        return userMapper.toDto(user);
    }
    
    // ================= DELETE =================
    
    /**
     * Удаление пользователя с публикацией события.
     *
     * <p>existsById заменён на findById: событию нужен email удаляемого
     * пользователя, а повторный запрос дешевле держать в одном SELECT.
     * delete(user) с managed-сущностью выполняет REMOVE без дополнительных
     * выборок (deleteById(id) внутри сам делает findById).
     */
    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        
        userRepository.delete(user);
        
        eventPublisher.publishEvent(new UserDeletedEvent(user.getId(), user.getEmail()));
        
        log.info("Удален пользователь id={}", id);
    }
    
    // ============ приватные помощники (бизнес-правила) ============
    
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
    
    private void checkEmailFree(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ValidationException("Email уже занят: " + email);
        }
    }
    
    private void checkEmailNotOccupiedByAnother(String email, Long id) {
        userRepository.findByEmail(email).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ValidationException("Email уже занят другим пользователем: " + email);
            }
        });
    }
}