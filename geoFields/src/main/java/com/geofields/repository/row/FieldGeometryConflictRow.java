package com.geofields.repository.row;

/** Поле организации, геометрия которого пересекается с проверяемым контуром. */
public record FieldGeometryConflictRow(long fieldId, String fieldName) {
}
