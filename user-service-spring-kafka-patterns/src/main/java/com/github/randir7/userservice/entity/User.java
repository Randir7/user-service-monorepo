package com.github.randir7.userservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Сущность «Пользователь» — Java-объект, который Hibernate хранит в таблице
 * users базы PostgreSQL. Класс и таблица — два изображения одного и того же:
 * аннотации описывают, как поля объекта превращаются в колонки.
 *
 * <p><b>Соответствие полей и колонок таблицы users:</b>
 * <ul>
 *   <li>id (Long)              → id,         bigint, первичный ключ, автоинкремент
 *   <li>name (String)          → name,       varchar(100), NOT NULL
 *   <li>email (String)         → email,      varchar(255), NOT NULL, UNIQUE
 *   <li>age (Integer)          → age,        integer, NOT NULL
 *   <li>createdAt (LocalDateTime) → created_at, timestamp, NOT NULL;
 *       проставляется автоматически при INSERT и никогда не меняется
 * </ul>
 *
 * <p><b>Жизненный цикл объекта:</b>
 * <ul>
 *   <li>transient — «new User(...)»: Hibernate о нём не знает, id/createdAt null;
 *   <li>persistent — после save(): строка в БД есть, Hibernate отслеживает объект;
 *   <li>detached — сессия закрыта, данные при объекте остались. В текущей
 *  *     архитектуре до этого состояния не доводят: сервис загружает пользователя
 *  *     внутри @Transactional, сущность остается managed, и изменения уходят
 *  *     в БД через dirty checking при commit (см. UserService.update)..
 * </ul>
 *
 * <p><b>Требования JPA к сущности:</b> класс не final (Hibernate создаёт
 * подклассы-прокси), есть конструктор без аргументов (объекты строятся
 * рефлексией, поля заполняются напрямую, минуя конструктор и сеттеры).
 */

@Entity
@Table(name = "users")
public class User {
    // Первичный ключ. Генерирует база (IDENTITY = автоинкремент),
    // поэтому сеттера нет — id устанавливается только автоматически
    // Long, а не long: null означает «объект ещё не сохранён».
    // при примитиве было бы значение по-умолчанию 0
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;
    
    @Column(name = "age", nullable = false)
    private Integer age;
    
    
// Дата создания записи: timestamp, NOT NULL. Заполняется Hibernate
// при INSERT (@CreationTimestamp). updatable = false — поле не попадает
// в UPDATE-запросы: дата создания неизменна.
// Сеттера нет по той же причине, что и у id
// (таймштамп ставится только автоматически)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Требование JPA: пустой конструктор, через него Hibernate создаёт объекты
    protected User() {
    }
    
   
// Создание НОВОГО пользователя в бизнес-коде (вызывает UserService).
// id и createdAt не принимаем: их назначат база и Hibernate.
    public User(String name, String email, Integer age) {
        this.name = name;
        this.email = email;
        this.age = age;
    }
    
// Конструктор для реконструкции уже существующего пользователя
// id известен — как будто прочитан из БД
// Нужен для легкой работы тестовых классов
// Бизнес-код все так же создаёт НОВЫХ пользователей конструктором без id
    
    public User(Long id, String name, String email, Integer age) {
        this(name, email, age);
        this.id = id;
    }
    
    // Сеттеры — только у полей, которые разрешено менять после создания.
    public Long getId() { return id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    @Override
    public String toString() {
        return "User{id=%d, name='%s', email='%s', age=%d, createdAt=%s}"
                .formatted(id, name, email, age, createdAt);
    }
}