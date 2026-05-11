package com.geofields.repository;

import com.geofields.repository.row.FieldGeometryConflictRow;
import com.geofields.repository.row.FieldHistoryRow;

import java.util.Collection;
import java.util.List;

// Контракт репозитория для работы с таблицей fields.
public interface FieldRepository {
    List<FieldHistoryRow> findAllFieldsWithHistory(Long organizationId);

    boolean fieldBelongsToOrganization(Long fieldId, Long organizationId);

    int updateFieldActiveStatus(long fieldId, long organizationId, boolean active);

    int deleteField(long fieldId, long organizationId);

    List<FieldHistoryRow> findIntersectingFieldsWithHistory(long organizationId, long fieldId);

    long insertField(String fieldName, String geometryGeoJson);

    void saveFieldIntersections(long organizationId, long fieldId, Collection<Long> intersectingFieldIds);

    /**
     * Поля организации, чья геометрия пересекается с переданным GeoJSON geometry (SRID 4326).
     *
     * @param geometryGeoJson JSON объекта geometry, например {@code {"type":"Polygon","coordinates":...}}
     * @param excludeFieldId  при редактировании поля — исключить его из проверки; иначе {@code null}
     */
    List<FieldGeometryConflictRow> findFieldsIntersectingGeometry(
            long organizationId,
            String geometryGeoJson,
            Long excludeFieldId);
}
