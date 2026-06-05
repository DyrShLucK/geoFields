package com.geofields.controllers;

import com.geofields.service.ElevationPythonClient;
import com.geofields.service.NdviTileFetchService;
import com.geofields.service.RequestValidationService;
import com.geofields.service.TerrainTileFetchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElevationControllerTest {

    @Mock
    private RequestValidationService validationService;

    @Mock
    private ElevationPythonClient elevationPythonClient;

    @Mock
    private TerrainTileFetchService terrainTileFetchService;

    @InjectMocks
    private ElevationController elevationController;

    @Test
    void prepareElevation_returnsUnauthorized_whenNoOrg() {
        when(validationService.requireOrganizationId()).thenReturn(
                RequestValidationService.ValidationResult.error(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));

        ResponseEntity<?> response = elevationController.prepareElevation("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void prepareElevation_rewritesTileUrls_whenPythonReturnsAbsolutePath() {
        mockAuthorizedField("1", 1L);
        Map<String, Object> pythonBody = Map.of(
                "status", "ready",
                "tile_url", "/tiles/elevation/1/{z}/{x}/{y}.png",
                "slope_tile_url", "/tiles/slope/1/{z}/{x}/{y}.png",
                "elevation_min", 100.0,
                "elevation_max", 200.0);
        when(elevationPythonClient.prepareElevation(1L)).thenReturn(pythonBody);

        ResponseEntity<?> response = elevationController.prepareElevation("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("tile_url")).isEqualTo("/tiles/elevation/1/{z}/{x}/{y}.png");
        assertThat(body.get("slope_tile_url")).isEqualTo("/tiles/slope/1/{z}/{x}/{y}.png");
    }

    @Test
    void prepareElevation_returnsBadGateway_whenPythonUnavailable() {
        mockAuthorizedField("1", 1L);
        when(elevationPythonClient.prepareElevation(1L))
                .thenThrow(new ResourceAccessException("python down"));

        ResponseEntity<?> response = elevationController.prepareElevation("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Python elevation service unavailable"));
    }

    private void mockAuthorizedField(String fieldIdStr, long fieldId) {
        when(validationService.requireOrganizationId())
                .thenReturn(RequestValidationService.ValidationResult.ok(10L));
        when(validationService.requireOwnedFieldId(fieldIdStr, 10L, false))
                .thenReturn(RequestValidationService.ValidationResult.ok(fieldId));
    }
}
