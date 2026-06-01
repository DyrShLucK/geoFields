package com.geofields.support.web;

import org.springframework.http.ResponseEntity;

/** Вспомогательные методы для ответов контроллеров после {@code RequestValidationService}. */
public final class ValidationResponses {

    private ValidationResponses() {
    }

    @SuppressWarnings("unchecked")
    public static <T> ResponseEntity<T> castError(ResponseEntity<?> errorResponse) {
        return (ResponseEntity<T>) errorResponse;
    }
}
