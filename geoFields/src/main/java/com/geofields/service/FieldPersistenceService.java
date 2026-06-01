package com.geofields.service;

import com.geofields.repository.FieldRepository;
import com.geofields.repository.row.FieldGeometryConflictRow;
import org.springframework.stereotype.Service;

import java.util.List;

/** Создание поля в БД с расчётом пересечений контуров. */
@Service
public class FieldPersistenceService {

    public record CreatedField(long fieldId, int conflictCount) {}

    private final FieldRepository fieldRepository;

    public FieldPersistenceService(FieldRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    public CreatedField createWithIntersections(long organizationId, String fieldName, String geometryGeoJson) {
        List<FieldGeometryConflictRow> conflicts =
                fieldRepository.findFieldsIntersectingGeometry(organizationId, geometryGeoJson, null);
        long fieldId = fieldRepository.insertField(fieldName, geometryGeoJson);
        fieldRepository.saveFieldIntersections(
                organizationId,
                fieldId,
                conflicts.stream().map(FieldGeometryConflictRow::fieldId).toList());
        return new CreatedField(fieldId, conflicts.size());
    }
}
