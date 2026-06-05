package com.geofields.controllers;

import com.geofields.repository.NdviAnalyticsRepository;
import com.geofields.repository.OrganizationRepository;
import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.service.FieldIdRequestParser;
import com.geofields.service.FieldGeoJsonQueryService;
import com.geofields.service.NdviExternalFetchPlaceholder;
import com.geofields.service.RequestValidationService;
import com.geofields.support.web.ValidationResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Legacy NDVI-заглушки для фронта. Реальные тайлы и тренды — через {@code NdviPythonProxyController} → Python.
 */
@RestController
public class NdviTileController {

    private final RequestValidationService validationService;
    private final OrganizationRepository organizationRepository;
    private final NdviAnalyticsRepository ndviAnalyticsRepository;
    private final NdviExternalFetchPlaceholder ndviExternalFetchPlaceholder;
    private final FieldGeoJsonQueryService fieldGeoJsonQueryService;

    public NdviTileController(
            RequestValidationService validationService,
            OrganizationRepository organizationRepository,
            NdviAnalyticsRepository ndviAnalyticsRepository,
            NdviExternalFetchPlaceholder ndviExternalFetchPlaceholder,
            FieldGeoJsonQueryService fieldGeoJsonQueryService) {
        this.validationService = validationService;
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
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ValidationResponses.castError(org.errorResponse());
        RequestValidationService.ValidationResult<LocalDate> targetDate = validationService.parseDate(date);
        if (targetDate.isError()) return ValidationResponses.castError(targetDate.errorResponse());

        long orgId = org.value();
        Optional<Long> fieldId = ndviAnalyticsRepository.findFieldIdCoveringPoint(orgId, lat, lon);
        FieldFeatureCollectionDto geo = fieldId
                .map(fid -> fieldGeoJsonQueryService.loadFeatureSubsetByFieldIds(orgId, List.of(fid)))
                .orElseGet(NdviTileController::emptyFeatureCollection);
        ndviExternalFetchPlaceholder.requestValueForPoint(orgId, lat, lon, targetDate.value(), fieldId.orElse(null), geo);

        return ResponseEntity.ok(Map.of("value", 0.65));
    }

    @GetMapping("/get_ndvi_by_id")
    public ResponseEntity<Map<String, String>> getNdviById(
            @RequestParam String field_id,
            @RequestParam String date) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ValidationResponses.castError(org.errorResponse());
        RequestValidationService.ValidationResult<Long> field =
                validationService.requireOwnedFieldId(field_id, org.value(), false);
        if (field.isError()) return ValidationResponses.castError(field.errorResponse());
        RequestValidationService.ValidationResult<LocalDate> targetDate = validationService.parseDate(date);
        if (targetDate.isError()) return ValidationResponses.castError(targetDate.errorResponse());

        FieldFeatureCollectionDto geo = fieldGeoJsonQueryService.loadFeatureSubsetByFieldIds(org.value(), List.of(field.value()));
        ndviExternalFetchPlaceholder.requestTileForField(org.value(), field.value(), targetDate.value(), geo);
        return ResponseEntity.ok(Map.of("url", stubTileUrlForField(field.value())));
    }

    @GetMapping("/get_all_ndvi_tile")
    public ResponseEntity<Map<String, String>> getAllNdviTile(
            @RequestParam String date,
            @RequestParam(required = false) List<String> field_ids) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ValidationResponses.castError(org.errorResponse());
        RequestValidationService.ValidationResult<LocalDate> targetDate = validationService.parseDate(date);
        if (targetDate.isError()) return ValidationResponses.castError(targetDate.errorResponse());
        RequestValidationService.ValidationResult<List<Long>> targets = resolveTargetFieldIds(org.value(), field_ids);
        if (targets.isError()) return ValidationResponses.castError(targets.errorResponse());

        long orgId = org.value();
        LocalDate recordDate = targetDate.value();
        List<Long> targetFieldIds = targets.value();
        String orgName = organizationRepository.findNameById(orgId).orElse("");

        if (targetFieldIds.isEmpty()) {
            ndviExternalFetchPlaceholder.requestTileForFields(orgId, List.of(), recordDate, emptyFeatureCollection());
            return ResponseEntity.ok(buildOrgTileBody(orgId, orgName, recordDate, stubTileUrlForOrgAll(orgId)));
        }

        FieldFeatureCollectionDto geo =
                fieldGeoJsonQueryService.loadFeatureSubsetByFieldIds(orgId, targetFieldIds);
        ndviExternalFetchPlaceholder.requestTileForFields(orgId, targetFieldIds, recordDate, geo);
        return ResponseEntity.ok(buildOrgTileBody(orgId, orgName, recordDate, stubTileUrlForOrgAll(orgId)));
    }

    private Map<String, String> buildOrgTileBody(long orgId, String orgName, LocalDate recordDate, String url) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("organizationName", orgName);
        body.put("date", recordDate.toString());
        return body;
    }

    private RequestValidationService.ValidationResult<List<Long>> resolveTargetFieldIds(long orgId, List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return RequestValidationService.ValidationResult.ok(
                    ndviAnalyticsRepository.findDistinctFieldIdsForOrganization(orgId));
        }
        final List<Long> parsed;
        try {
            parsed = FieldIdRequestParser.parseFieldIds(rawIds);
        } catch (IllegalArgumentException ex) {
            return RequestValidationService.ValidationResult.error(
                    ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())));
        }
        if (parsed.isEmpty()) {
            return RequestValidationService.ValidationResult.ok(
                    ndviAnalyticsRepository.findDistinctFieldIdsForOrganization(orgId));
        }
        RequestValidationService.ValidationResult<List<Long>> fieldAccess =
                validationService.ensureFieldsBelongToOrganization(parsed, orgId, true);
        if (fieldAccess.isError()) {
            return RequestValidationService.ValidationResult.error(fieldAccess.errorResponse());
        }
        return RequestValidationService.ValidationResult.ok(parsed);
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
