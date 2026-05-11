package com.geofields.controllers;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.repository.FieldRepository;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.service.FieldGeoJsonService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FieldController {

    private final FieldGeoJsonService fieldGeoJsonService;
    private final FieldRepository fieldRepository;

    public FieldController(FieldGeoJsonService fieldGeoJsonService, FieldRepository fieldRepository) {
        this.fieldGeoJsonService = fieldGeoJsonService;
        this.fieldRepository = fieldRepository;
    }

    @GetMapping("/get_fields")
    public FieldFeatureCollectionDto getFields() {
        return fieldGeoJsonService.getFieldsAsFeatureCollection();
    }

    @GetMapping("/api/fields/{fieldId}/intersections")
    public ResponseEntity<FieldFeatureCollectionDto> getIntersectingFields(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long fieldId) {
        Long organizationId = user != null ? user.getOrganizationId() : null;
        if (organizationId == null || !fieldRepository.fieldBelongsToOrganization(fieldId, organizationId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(fieldGeoJsonService.getIntersectingFieldsAsFeatureCollection(organizationId, fieldId));
    }
}
