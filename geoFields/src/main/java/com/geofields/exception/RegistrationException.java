package com.geofields.exception;

// Ошибка регистрации с понятным текстом для пользователя (организация / занятый логин и т.д.).
public class RegistrationException extends RuntimeException {

    public RegistrationException(String message) {
        super(message);
    }
}
