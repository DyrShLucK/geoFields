package com.geofields.controllers;

import com.geofields.repository.FieldRepository;
import com.geofields.repository.NdviAnalyticsRepository;
import com.geofields.repository.OrganizationRepository;
import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.service.AuthContextService;
import com.geofields.service.FieldGeoJsonQueryService;
import com.geofields.service.NdviExternalFetchPlaceholder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
public class NdviTileController {

    private final AuthContextService authContextService;
    private final FieldRepository fieldRepository;
    private final OrganizationRepository organizationRepository;
    private final NdviAnalyticsRepository ndviAnalyticsRepository;
    private final NdviExternalFetchPlaceholder ndviExternalFetchPlaceholder;
    private final FieldGeoJsonQueryService fieldGeoJsonQueryService;

    public NdviTileController(
            AuthContextService authContextService,
            FieldRepository fieldRepository,
            OrganizationRepository organizationRepository,
            NdviAnalyticsRepository ndviAnalyticsRepository,
            NdviExternalFetchPlaceholder ndviExternalFetchPlaceholder,
            FieldGeoJsonQueryService fieldGeoJsonQueryService) {
        this.authContextService = authContextService;
        this.fieldRepository = fieldRepository;
        this.organizationRepository = organizationRepository;
        this.ndviAnalyticsRepository = ndviAnalyticsRepository;
        this.ndviExternalFetchPlaceholder = ndviExternalFetchPlaceholder;
        this.fieldGeoJsonQueryService = fieldGeoJsonQueryService;
    }

    @GetMapping("/get_ndvi_value")
    public ResponseEntity<Map<String, Object>> getNdviValue(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam String date) {
        Long orgId = authContextService.getCurrentOrganizationId();
        if (orgId == null) {
            return ResponseEntity.status(401).build();
        }
        final LocalDate recordDate;
        try {
            recordDate = LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректная date, ожидается YYYY-MM-DD"));
        }

        Optional<Long> fieldId = ndviAnalyticsRepository.findFieldIdCoveringPoint(orgId, lat, lon);
        Optional<String> fromDb = fieldId.flatMap(fid ->
                ndviAnalyticsRepository.findNdviTileUrlForFieldOnDate(fid, orgId, recordDate));

        if (fromDb.isEmpty()) {
            FieldFeatureCollectionDto geo = fieldId
                    .map(fid -> fieldGeoJsonQueryService.loadFeatureSubsetByFieldIds(orgId, List.of(fid)))
                    .orElseGet(NdviTileController::emptyFeatureCollection);
            ndviExternalFetchPlaceholder.requestValueForPoint(orgId, lat, lon, recordDate, fieldId.orElse(null), geo);
        }

        return ResponseEntity.ok(Map.of("value", 0.65));
    }

    @GetMapping("/get_ndvi_by_id")
    public ResponseEntity<Map<String, String>> getNdviById(
            @RequestParam String field_id,
            @RequestParam String date) {
        Long orgId = authContextService.getCurrentOrganizationId();
        if (orgId == null) {
            return ResponseEntity.status(401).build();
        }
        long fieldId;
        try {
            fieldId = Long.parseLong(field_id.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный field_id"));
        }
        if (!fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
            return ResponseEntity.notFound().build();
        }
        final LocalDate recordDate;
        try {
            recordDate = LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректная date, ожидается YYYY-MM-DD"));
        }

        Optional<String> fromDb = ndviAnalyticsRepository.findNdviTileUrlForFieldOnDate(fieldId, orgId, recordDate);
        if (fromDb.isPresent()) {
            return ResponseEntity.ok(Map.of("url", fromDb.get()));
        }

        FieldFeatureCollectionDto geo = fieldGeoJsonQueryService.loadFeatureSubsetByFieldIds(orgId, List.of(fieldId));
        ndviExternalFetchPlaceholder.requestTileForField(orgId, fieldId, recordDate, geo);
        return ResponseEntity.ok(Map.of("url", stubTileUrlForField(fieldId)));
    }

    /**
     * NDVI-плитка для списка полей организации (или всех полей орг., если {@code field_ids} не переданы).
     */
    @GetMapping("/get_all_ndvi_tile")
    public ResponseEntity<Map<String, String>> getAllNdviTile(
            @RequestParam String date,
            @RequestParam(required = false) List<String> field_ids) {
        Long orgId = authContextService.getCurrentOrganizationId();
        if (orgId == null) {
            return ResponseEntity.status(401).build();
        }
        final LocalDate recordDate;
        try {
            recordDate = LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректная date, ожидается YYYY-MM-DD"));
        }

        List<Long> targetFieldIds;
        try {
            targetFieldIds = resolveTargetFieldIds(orgId, field_ids);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        String orgName = organizationRepository.findNameById(orgId).orElse("");

        if (targetFieldIds.isEmpty()) {
            ndviExternalFetchPlaceholder.requestTileForFields(orgId, List.of(), recordDate, emptyFeatureCollection());
            return ResponseEntity.ok(buildOrgTileBody(orgId, orgName, recordDate, stubTileUrlForOrgAll(orgId)));
        }

        String firstUrl = null;
        for (Long fieldId : targetFieldIds) {
            Optional<String> url = ndviAnalyticsRepository.findNdviTileUrlForFieldOnDate(fieldId, orgId, recordDate);
            if (url.isEmpty()) {
                FieldFeatureCollectionDto geo =
                        fieldGeoJsonQueryService.loadFeatureSubsetByFieldIds(orgId, targetFieldIds);
                ndviExternalFetchPlaceholder.requestTileForFields(orgId, targetFieldIds, recordDate, geo);
                return ResponseEntity.ok(
                        buildOrgTileBody(orgId, orgName, recordDate, stubTileUrlForOrgAll(orgId)));
            }
            if (firstUrl == null) {
                firstUrl = url.get();
            }
        }

        return ResponseEntity.ok(buildOrgTileBody(orgId, orgName, recordDate, firstUrl));
    }

    private Map<String, String> buildOrgTileBody(long orgId, String orgName, LocalDate recordDate, String url) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("organizationName", orgName);
        body.put("date", recordDate.toString());
        return body;
    }

    private List<Long> resolveTargetFieldIds(long orgId, List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return ndviAnalyticsRepository.findDistinctFieldIdsForOrganization(orgId);
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (String raw : rawIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    unique.add(Long.parseLong(t));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Некорректный field_ids: " + part);
                }
            }
        }
        if (unique.isEmpty()) {
            return ndviAnalyticsRepository.findDistinctFieldIdsForOrganization(orgId);
        }
        List<Long> parsed = new ArrayList<>(unique);
        for (Long fieldId : parsed) {
            if (!fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
                throw new IllegalArgumentException("Поле " + fieldId + " не принадлежит организации");
            }
        }
        return parsed;
    }

    private static String stubTileUrlForField(long fieldId) {
        return "https://earthengine.googleapis.com/v1alpha/projects/stub/maps/FIELD_" + fieldId
                + "/tiles/{z}/{x}/{y}?token=stub";
    }

    private static String stubTileUrlForOrgAll(long orgId) {
        return "https://earthengine.googleapis.com/v1alpha/projects/stub/maps/ORG_" + orgId
                + "_ALL/tiles/{z}/{x}/{y}?token=stub";
    }

    private static FieldFeatureCollectionDto emptyFeatureCollection() {
        return new FieldFeatureCollectionDto("FeatureCollection", List.of());
    }
}
