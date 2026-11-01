package com.github.randir7.userservice.repository;

import com.github.randir7.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий пользователей — замена ручному UserDao/UserDaoImpl.
 *
 * <p>Ни одной строчки реализации: Spring Data JPA сгенерирует
 * реализацию этого интерфейса автоматически при старте приложения.
 *
 * <p><b>Что может JpaRepository<User, Long> из коробки:</b>
 * - save(entity)          → INSERT (или UPDATE, если entity уже в БД)
 * - findById(id)          → SELECT по первичному ключу, вернет Optional
 * - existsById(id)        → SELECT count(*) ... (быстрая проверка существования)
 * - findAll() / findAll(Sort) → SELECT всех записей (с сортировкой)
 * - deleteById(id)        → DELETE
 * - count()               → SELECT count(*)
 *
 * <p>Параметры дженериков: <ТипСущности, ТипПервичногоКлюча>.
 *
 * <b>Производные запросы (derived queries):</b>
 * Spring разберет ИМЕНА методов ниже и сам построит SQL:
 * - findByEmail(String)   → WHERE email = ?
 * - existsByEmail(String) → SELECT count(*)>0 WHERE email = ?
 * Если ошибиться в имени (findByEmal), приложение упадет ПРИ СТАРТЕ
 * с понятной ошибкой — это плюс: опечатки ловятся сразу, а не в рантайме.
 *
 * <p>@Repository здесь необязателен (Spring Data и так найдет интерфейс),
 * но это хорошая самодокументирующая практика.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /** Найти пользователя по email (email уникален, поэтому Optional). */
    Optional<User> findByEmail(String email);
    
    /** Существует ли пользователь с таким email? Быстрее, чем доставать всю запись. */
    boolean existsByEmail(String email);
}