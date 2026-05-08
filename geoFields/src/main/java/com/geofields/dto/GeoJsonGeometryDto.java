package com.geofields.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
// coordinates — фрагмент валидного JSON (массив), в ответе встраивается как есть (@JsonRawValue).
// Так в HTTP/Redis всегда обычные JSON-массивы, без «объектов с ключами 0,1» от JsonNode.
public record GeoJsonGeometryDto(
        String type,
        @JsonRawValue String coordinates
) {
}
