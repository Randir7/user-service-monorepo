package com.github.randir7.userservice.exception;

/**
 * Данные не прошли бизнес-валидацию.
 */
public class ValidationException extends RuntimeException {
    
    /**
     * @param message текст для пользователя, например
     *                «Возраст должен быть в диапазоне от 0 до 150».
     *                Единственный «груз» исключения — опустить его нельзя.
     */
    public ValidationException(String message) {
        super(message);
    }
}