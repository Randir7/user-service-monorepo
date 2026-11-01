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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты UserService.
 *
 * <p><b>@ExtendWith(MockitoExtension.class)</b> — подключает расширение
 * Mockito для JUnit 5: оно само создаст моки для полей с @Mock
 * ПЕРЕД каждым тестом и проверит корректность их использования после.
 *
 * <p><b>Стратегия мокирования:</b>
 * - UserRepository — МОК: это граница с внешним миром (БД).
 * - UserMapper — используется НАСТОЯЩИЙ: это чистая функция без зависимостей
 *   («вход X → выход Y»). Мокать нет смысла, т.к. тест перестанет
 *   проверять реальное преобразование.
 * - UserService — ОБЪЕКТ ТЕСТИРОВАНИЯ: создается вручную через конструктор.
 *   Благодаря конструкторной инъекции не нужен Spring-контекст — достаточно обычного new
 *
 * <p><b>Имена методов декларативны:</b> [метод][WithУсловие]Should[Ожидание] —
 * упавший тест сразу указывает на сломанное поведение.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;   // мок: «фальшивая» БД
    
    private UserMapper userMapper;           // настоящий маппер
    private UserService userService;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @BeforeEach
    void setUp() {
        userMapper = new UserMapper();
        userService = new UserService(userRepository, userMapper, eventPublisher);  // НОВОЕ
    }
    
    // ==================== CREATE ====================
    
    @Test
    @DisplayName("create: публикует UserCreatedEvent с данными сохранённого пользователя")
    void createShouldPublishUserCreatedEventWithSavedUserData() {
        CreateUserRequest request = new CreateUserRequest("Ivan", "ivan@example.com", 28);
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        
        userService.create(request);
        
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .isInstanceOf(UserCreatedEvent.class);
        UserCreatedEvent event = (UserCreatedEvent) captor.getValue();
        assertThat(event.email()).isEqualTo("ivan@example.com");
    }
    
    @Test
    @DisplayName("create: передаёт в save() нормализованные данные (trim, lowerCase)")
    void createShouldSendNormalizedDataToSaveMethod() {
        // запрос с «грязными» данными (пробелы, верхний регистр)
        CreateUserRequest request = new CreateUserRequest("  Ivan  ", "IVAN@Example.COM", 28);
        
        // настройка поведения мока
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        // thenAnswer: вернуть ТОТ ЖЕ объект, что передали в save() —
        // имитация поведения Spring Data (save возвращает сохраненную сущность)
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        
        userService.create(request);
        
        // проверка, ЧТО ИМЕННО ушло в репозиторий (ArgumentCaptor!)
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedEntity = captor.getValue();
        assertThat(savedEntity.getName()).isEqualTo("Ivan");              // trim сработал
        assertThat(savedEntity.getEmail()).isEqualTo("ivan@example.com"); // lowerCase сработал
        assertThat(savedEntity.getAge()).isEqualTo(28);
        assertThat(savedEntity.getId()).isNull();  // id назначит БД, не сервис!
    }
    
    @Test
    @DisplayName("create: возвращает DTO с нормализованными данными")
    void createShouldReturnDtoWithNormalizedData() {
        CreateUserRequest request = new CreateUserRequest("  Ivan  ", "IVAN@Example.COM", 28);
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        
        UserDto result = userService.create(request);
        
        assertThat(result.name()).isEqualTo("Ivan");
        assertThat(result.email()).isEqualTo("ivan@example.com");
        assertThat(result.age()).isEqualTo(28);
    }
    
    @Test
    @DisplayName("create: бросает ValidationException, если email уже занят, и НЕ сохраняет")
    void createWithOccupiedEmailShouldThrowValidationExceptionAndNotSave() {
        
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(true);
        CreateUserRequest request = new CreateUserRequest("Ivan", "ivan@example.com", 28);
        
        
        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ivan@example.com");
        
        // Критично: сохранение НЕ должно произойти!
        verify(userRepository, never()).save(any());
    }
    
    // ==================== READ ====================
    
    @Test
    @DisplayName("getById: возвращает DTO найденного пользователя")
    void getByIdShouldReturnDtoOfFoundUser() {
        
        User user = new User(1L, "Ivan", "ivan@example.com", 28);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        
        UserDto result = userService.getById(1L);
        
        
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Ivan");
        assertThat(result.email()).isEqualTo("ivan@example.com");
    }
    
    @Test
    @DisplayName("getById: бросает UserNotFoundException, если пользователя нет")
    void getByIdWithNonExistentIdShouldThrowUserNotFoundException() {
        
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        
        
        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
    
    @Test
    @DisplayName("findAll: возвращает список DTO в порядке возрастания id")
    void findAllShouldReturnDtoListSortedById() {
        
        when(userRepository.findAll(Sort.by("id"))).thenReturn(List.of(
                new User(1L, "Ivan", "ivan@example.com", 28),
                new User(2L, "Petr", "petr@example.com", 30)
        ));
        
        
        List<UserDto> result = userService.findAll();
        
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(2L);
    }
    
    // ==================== UPDATE ====================
    
    @Test
    @DisplayName("update: изменяет managed-сущность без вызова save (dirty checking)")
    void updateShouldModifyManagedUserWithoutCallingSave() {
        
        User existing = new User(1L, "Ivan", "ivan@example.com", 28);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        
        
        UserDto result = userService.update(1L,
                new UpdateUserRequest("Ivan P.", "NEW@example.com", 29));
        
        
        assertThat(result.name()).isEqualTo("Ivan P.");
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.age()).isEqualTo(29);
        
        // save() НЕ вызывается,
        // изменения уйдут в БД через dirty checking при commit транзакции.
        verify(userRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("update: позволяет оставить СОБСТВЕННЫЙ email пользователя")
    void updateWithOwnEmailShouldSucceed() {
        
        User existing = new User(1L, "Ivan", "ivan@example.com", 28);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        // findByEmail находит САМОГО обновляемого пользователя — это НЕ конфликт
        when(userRepository.findByEmail("ivan@example.com")).thenReturn(Optional.of(existing));
        
        
        UserDto result = userService.update(1L,
                new UpdateUserRequest("Ivan P.", "ivan@example.com", 29));
        
        
        assertThat(result.email()).isEqualTo("ivan@example.com");
        assertThat(result.name()).isEqualTo("Ivan P.");
    }
    
    @Test
    @DisplayName("update: бросает ValidationException, если email занят ДРУГИМ пользователем")
    void updateWithEmailOccupiedByAnotherUserShouldThrowValidationException() {
        
        User existing = new User(1L, "Ivan", "ivan@example.com", 28);
        User other = new User(2L, "Petr", "new@example.com", 30);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.of(other));
        
       
        assertThatThrownBy(() -> userService.update(1L,
                new UpdateUserRequest("Ivan P.", "new@example.com", 29)))
                .isInstanceOf(ValidationException.class);
    }
    
    @Test
    @DisplayName("update: бросает UserNotFoundException для несуществующего id")
    void updateWithNonExistentIdShouldThrowUserNotFoundException() {
        
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        
        
        assertThatThrownBy(() -> userService.update(99L,
                new UpdateUserRequest("X", "x@example.com", 1)))
                .isInstanceOf(UserNotFoundException.class);
    }
    
    // ==================== DELETE ====================
    
    @Test
    @DisplayName("delete: удаляет найденного пользователя и публикует UserDeletedEvent")
    void deleteShouldDeleteFoundUserAndPublishUserDeletedEvent() {
        User user = new User(1L, "Ivan", "ivan@example.com", 28);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        userService.delete(1L);
        
        verify(userRepository).delete(user);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(UserDeletedEvent.class);
        assertThat(((UserDeletedEvent) captor.getValue()).email()).isEqualTo("ivan@example.com");
    }
    
    @Test
    @DisplayName("delete: бросает UserNotFoundException и НЕ публикует событие")
    void deleteWithNonExistentIdShouldThrowUserNotFoundExceptionAndNotPublishEvent() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(UserNotFoundException.class);
        
        verify(userRepository, never()).delete(any());
        verifyNoInteractions(eventPublisher);
    }
    
    
}