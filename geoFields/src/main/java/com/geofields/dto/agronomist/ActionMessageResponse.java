package com.geofields.dto.agronomist;

public record ActionMessageResponse(String message, long id) {
    public ActionMessageResponse(String message) {
        this(message, 0L);
    }
}
