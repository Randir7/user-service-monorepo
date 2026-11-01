package com.github.randir7.userservice.exception;

/**
 * Штатная ситуация (НЕ авария): пользователь с указанным id не найден в БД.
 */
public class UserNotFoundException extends RuntimeException {
    
    /**
     * @param id идентификатор, который искали и не нашли.
     *           Конструктор принимает только id: текст собирается здесь же,
     *           поэтому формат сообщения одинаков во всём приложении.
     */
    public UserNotFoundException(Long id) {
        super("Пользователь с id=" + id + " не найден");
    }
}