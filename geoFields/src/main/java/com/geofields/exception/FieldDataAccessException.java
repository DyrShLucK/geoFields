package com.geofields.exception;

// Своя ошибка домена доступа к данным полей.
public class FieldDataAccessException extends RuntimeException {
    public FieldDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
