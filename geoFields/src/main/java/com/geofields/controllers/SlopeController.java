package com.geofields.controllers;

import com.geofields.service.RequestValidationService;
import com.geofields.service.SlopePythonClient;
import com.geofields.support.web.ValidationResponses;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@RestController
public class SlopeController {

    private final RequestValidationService validationService;
    private final SlopePythonClient slopePythonClient;

    public SlopeController(
            RequestValidationService validationService,
            SlopePythonClient slopePythonClient) {
        this.validationService = validationService;
        this.slopePythonClient = slopePythonClient;
    }

    @GetMapping("/get_slope_tiles_for_field")
    public ResponseEntity<?> getSlopeTilesForField(
            @RequestParam String field_id,
            @RequestParam String date) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ValidationResponses.castError(org.errorResponse());
        RequestValidationService.ValidationResult<Long> field = validationService.parseFieldId(field_id);
        if (field.isError()) return ValidationResponses.castError(field.errorResponse());
        RequestValidationService.ValidationResult<Void> fieldAccess =
                validationService.ensureFieldBelongsToOrganization(field.value(), org.value(), false);
        if (fieldAccess.isError()) return ValidationResponses.castError(fieldAccess.errorResponse());
        RequestValidationService.ValidationResult<java.time.LocalDate> targetDate = validationService.parseDate(date);
        if (targetDate.isError()) return ValidationResponses.castError(targetDate.errorResponse());
        try {
            return ResponseEntity.ok(slopePythonClient.getTilesForField(field.value(), targetDate.value().toString()));
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", slopePythonClient.upstreamDetail(ex)));
        } catch (ResourceAccessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Python slope service unavailable"));
        }
    }

    @GetMapping("/get_slope_trend")
    public ResponseEntity<?> getSlopeTrend(
            @RequestParam("field_id") List<String> rawFieldIds,
            @RequestParam String start_date,
            @RequestParam String end_date) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ValidationResponses.castError(org.errorResponse());
        RequestValidationService.ValidationResult<List<Long>> fields = validationService.parseFieldIds(rawFieldIds);
        if (fields.isError()) return ValidationResponses.castError(fields.errorResponse());
        List<Long> fieldIds = fields.value();
        for (Long fieldId : fieldIds) {
            RequestValidationService.ValidationResult<Void> fieldAccess =
                    validationService.ensureFieldBelongsToOrganization(fieldId, org.value(), true);
            if (fieldAccess.isError()) return ValidationResponses.castError(fieldAccess.errorResponse());
        }
        RequestValidationService.ValidationResult<RequestValidationService.DateRange> range =
                validationService.parseDateRange(start_date, end_date);
        if (range.isError()) return ValidationResponses.castError(range.errorResponse());
        try {
            return ResponseEntity.ok(slopePythonClient.getTrend(
                    fieldIds,
                    range.value().startDate().toString(),
                    range.value().endDate().toString()));
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", slopePythonClient.upstreamDetail(ex)));
        } catch (ResourceAccessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Python slope service unavailable"));
        }
    }

    @GetMapping("/slope/tiles/{field_id}/{date}/{scene_id}/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> getSlopeTile(
            @PathVariable String field_id,
            @PathVariable String date,
            @PathVariable String scene_id,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        RequestValidationService.ValidationResult<Long> field = validationService.parseFieldId(field_id);
        if (field.isError()) return ResponseEntity.badRequest().build();
        RequestValidationService.ValidationResult<Void> fieldAccess =
                validationService.ensureFieldBelongsToOrganization(field.value(), org.value(), false);
        if (fieldAccess.isError()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        try {
            byte[] png = slopePythonClient.getTilePng(field_id, date, scene_id, z, x, y);
            if (png == null || png.length == 0) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 204) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (ResourceAccessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}

