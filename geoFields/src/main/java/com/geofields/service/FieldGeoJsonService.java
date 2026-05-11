package com.geofields.service;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.exception.FieldDataAccessException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class FieldGeoJsonService {
    private final AuthContextService authContextService;
    private final FieldGeoJsonQueryService fieldGeoJsonQueryService;

    public FieldGeoJsonService(AuthContextService authContextService, FieldGeoJsonQueryService fieldGeoJsonQueryService) {
        this.authContextService = authContextService;
        this.fieldGeoJsonQueryService = fieldGeoJsonQueryService;
    }

    public FieldFeatureCollectionDto getFieldsAsFeatureCollection() {
        return loadGeoJsonForCurrentOrganization("полей из БД", organizationId ->
                fieldGeoJsonQueryService.loadForOrganization(organizationId));
    }

    public FieldFeatureCollectionDto getIntersectingFieldsAsFeatureCollection(Long organizationId, long fieldId) {
        return loadGeoJson("пересечений полей из БД", organizationId, orgId ->
                fieldGeoJsonQueryService.loadIntersectingForField(orgId, fieldId));
    }

    private FieldFeatureCollectionDto loadGeoJsonForCurrentOrganization(
            String errorSuffix,
            java.util.function.Function<Long, FieldFeatureCollectionDto> loader) {
        Long organizationId = authContextService.getCurrentOrganizationId();
        return loadGeoJson(errorSuffix, organizationId, loader);
    }

    private FieldFeatureCollectionDto loadGeoJson(
            String errorSuffix,
            Long organizationId,
            java.util.function.Function<Long, FieldFeatureCollectionDto> loader) {
        try {
            return loader.apply(requireOrganizationId(organizationId));
        } catch (DataAccessException ex) {
            throw new FieldDataAccessException("Ошибка чтения " + errorSuffix + ": " + ex.getMessage(), ex);
        }
    }

    private Long requireOrganizationId(Long organizationId) {
        if (organizationId == null) {
            throw new IllegalStateException("У пользователя не задана организация");
        }
        return organizationId;
    }
}
