package com.geofields.controllers;

import com.geofields.service.NdviPythonClient;
import com.geofields.service.NdviTileFetchService;
import com.geofields.service.RequestValidationService;
import com.geofields.support.web.TileHttpResponses;
import com.geofields.support.web.ValidationResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/** Прокси NDVI: браузер → Java → Python (см. OpenAPIspec.yaml, тег PythonNDVI). */
@RestController
public class NdviPythonProxyController {

    private static final Logger log = LoggerFactory.getLogger(NdviPythonProxyController.class);

    private final RequestValidationService validationService;
    private final NdviPythonClient ndviPythonClient;
    private final NdviTileFetchService ndviTileFetchService;

    public NdviPythonProxyController(
            RequestValidationService validationService,
            NdviPythonClient ndviPythonClient,
            NdviTileFetchService ndviTileFetchService) {
        this.validationService = validationService;
        this.ndviPythonClient = ndviPythonClient;
        this.ndviTileFetchService = ndviTileFetchService;
    }

    @GetMapping("/get_ndvi_tiles_for_field")
    public ResponseEntity<?> getNdviTilesForField(
            @RequestParam String field_id,
            @RequestParam String date) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ValidationResponses.castError(org.errorResponse());
        RequestValidationService.ValidationResult<Long> field =
                validationService.requireOwnedFieldId(field_id, org.value(), false);
        if (field.isError()) return ValidationResponses.castError(field.errorResponse());
        RequestValidationService.ValidationResult<java.time.LocalDate> targetDate = validationService.parseDate(date);
        if (targetDate.isError()) return ValidationResponses.castError(targetDate.errorResponse());

        try {
            String normalizedDate = targetDate.value().toString();
            log.info("Getting NDVI tiles for field {} on date {}", field.value(), normalizedDate);
            return ResponseEntity.ok(ndviPythonClient.getTilesForField(field.value(), normalizedDate));
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ndviPythonClient.upstreamDetail(ex)));
        }
    }

    @GetMapping("/get_ndvi_trend")
    public ResponseEntity<?> getNdviTrend(
            @RequestParam("field_id") List<String> rawFieldIds,
            @RequestParam String start_date,
            @RequestParam String end_date,
            @RequestParam(name = "warmup", defaultValue = "true") boolean warmup) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ValidationResponses.castError(org.errorResponse());
        RequestValidationService.ValidationResult<List<Long>> fields = validationService.parseFieldIds(rawFieldIds);
        if (fields.isError()) return ValidationResponses.castError(fields.errorResponse());
        List<Long> fieldIds = fields.value();
        RequestValidationService.ValidationResult<List<Long>> fieldAccess =
                validationService.ensureFieldsBelongToOrganization(fieldIds, org.value(), true);
        if (fieldAccess.isError()) return ValidationResponses.castError(fieldAccess.errorResponse());
        RequestValidationService.ValidationResult<RequestValidationService.DateRange> range =
                validationService.parseDateRange(start_date, end_date);
        if (range.isError()) return ValidationResponses.castError(range.errorResponse());
        String normalizedStartDate = range.value().startDate().toString();
        String normalizedEndDate = range.value().endDate().toString();

        try {
            if (warmup) {
                ndviPythonClient.warmupTrendAnalytics(fieldIds, normalizedStartDate, normalizedEndDate);
            }
            return ResponseEntity.ok(ndviPythonClient.getTrend(fieldIds, normalizedStartDate, normalizedEndDate));
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ndviPythonClient.upstreamDetail(ex)));
        }
    }

    @GetMapping("/tiles/{field_id}/{date}/{scene_id}/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> getNdviTile(
            @PathVariable String field_id,
            @PathVariable String date,
            @PathVariable String scene_id,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        RequestValidationService.ValidationResult<Long> field =
                validationService.requireOwnedFieldId(field_id, org.value(), false);
        if (field.isError()) {
            return ResponseEntity.status(field.errorResponse().getStatusCode()).build();
        }

        try {
            NdviTileFetchService.FetchResult result = ndviTileFetchService.fetchTile(
                    field_id, date, scene_id, z, x, y);
            return TileHttpResponses.fromFetchResult(result, "X-Ndvi-Cache");
        } catch (RestClientResponseException ex) {
            log.warn("NDVI tile upstream HTTP error for field {} z/x/y={}/{}/{}: {}",
                    field_id, z, x, y, ndviPythonClient.upstreamDetail(ex));
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (ResourceAccessException ex) {
            log.error("NDVI tile upstream unreachable for field {} z/x/y={}/{}/{}: {}",
                    field_id, z, x, y, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
