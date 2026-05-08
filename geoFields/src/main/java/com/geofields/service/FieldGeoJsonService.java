package com.geofields.service;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.exception.FieldDataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class FieldGeoJsonService {
    private static final Logger log = LoggerFactory.getLogger(FieldGeoJsonService.class);

    private final AuthContextService authContextService;
    private final FieldGeoJsonQueryService fieldGeoJsonQueryService;

    public FieldGeoJsonService(AuthContextService authContextService, FieldGeoJsonQueryService fieldGeoJsonQueryService) {
        this.authContextService = authContextService;
        this.fieldGeoJsonQueryService = fieldGeoJsonQueryService;
    }

    /** GeoJSON полей текущей организации пользователя. */
    public FieldFeatureCollectionDto getFieldsAsFeatureCollection() {
        try {
            Long organizationId = authContextService.getCurrentOrganizationId();
            if (organizationId == null) {
                throw new IllegalStateException("У пользователя не задана организация");
            }
            return fieldGeoJsonQueryService.loadForOrganization(organizationId);
        } catch (DataAccessException ex) {
            throw new FieldDataAccessException("Ошибка чтения полей из БД: " + ex.getMessage(), ex);
        }
    }
}
