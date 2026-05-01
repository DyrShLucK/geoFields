package com.geofields.controllers;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.service.FieldGeoJsonService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FieldController {

    private final FieldGeoJsonService fieldGeoJsonService;

    public FieldController(FieldGeoJsonService fieldGeoJsonService) {
        this.fieldGeoJsonService = fieldGeoJsonService;
    }

    // Основная ручка: отдаем поля как GeoJSON FeatureCollection по OpenAPI.
    @GetMapping("/get_fields")
    public FieldFeatureCollectionDto getFields() {
        return fieldGeoJsonService.getFieldsAsFeatureCollection();
    }
}
