package com.geofields.controllers;

import com.geofields.service.ElevationPythonClient;
import com.geofields.service.NdviTileFetchService;
import com.geofields.service.RequestValidationService;
import com.geofields.service.TerrainTileFetchService;
import com.geofields.support.web.TileHttpResponses;
import com.geofields.support.web.ValidationResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/** Прокси рельефа/уклона (SRTM): браузер → Java → Python. */
@RestController
public class ElevationController {

    private final RequestValidationService validationService;
    private final ElevationPythonClient elevationPythonClient;
    private final TerrainTileFetchService terrainTileFetchService;

    public ElevationController(
            RequestValidationService validationService,
            ElevationPythonClient elevationPythonClient,
            TerrainTileFetchService terrainTileFetchService) {
        this.validationService = validationService;
        this.elevationPythonClient = elevationPythonClient;
        this.terrainTileFetchService = terrainTileFetchService;
    }

    @GetMapping("/prepare_elevation/{field_id}")
    public ResponseEntity<?> prepareElevation(@PathVariable String field_id) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ValidationResponses.castError(org.errorResponse());
        RequestValidationService.ValidationResult<Long> field =
                validationService.requireOwnedFieldId(field_id, org.value(), false);
        if (field.isError()) return ValidationResponses.castError(field.errorResponse());
        try {
            return ResponseEntity.ok(elevationPythonClient.prepareElevation(field.value()));
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", elevationPythonClient.upstreamDetail(ex)));
        } catch (ResourceAccessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Python elevation service unavailable"));
        }
    }

    @GetMapping("/tiles/elevation/{field_id}/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> getElevationTile(
            @PathVariable String field_id,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        return serveTerrainTile(field_id, z, x, y, true);
    }

    @GetMapping("/tiles/slope/{field_id}/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> getTerrainSlopeTile(
            @PathVariable String field_id,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        return serveTerrainTile(field_id, z, x, y, false);
    }

    private ResponseEntity<byte[]> serveTerrainTile(
            String fieldId,
            int z,
            int x,
            int y,
            boolean elevation) {
        RequestValidationService.ValidationResult<Long> org = validationService.requireOrganizationId();
        if (org.isError()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        RequestValidationService.ValidationResult<Long> field =
                validationService.requireOwnedFieldId(fieldId, org.value(), false);
        if (field.isError()) {
            return ResponseEntity.status(field.errorResponse().getStatusCode()).build();
        }
        try {
            NdviTileFetchService.FetchResult result = elevation
                    ? terrainTileFetchService.fetchElevationTile(fieldId, z, x, y)
                    : terrainTileFetchService.fetchSlopeTile(fieldId, z, x, y);
            return TileHttpResponses.fromFetchResult(result, "X-Terrain-Cache");
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
