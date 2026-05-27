package com.geofields.controllers;

import com.geofields.service.RequestValidationService;
import com.geofields.service.SlopePythonClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlopeControllerTest {

    @Mock
    private RequestValidationService validationService;

    @Mock
    private SlopePythonClient slopePythonClient;

    @InjectMocks
    private SlopeController slopeController;

    @Test
    void getSlopeTilesForField_returnsUnauthorized_whenNoOrg() {
        when(validationService.requireOrganizationId()).thenReturn(
                RequestValidationService.ValidationResult.error(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));

        ResponseEntity<?> response = slopeController.getSlopeTilesForField("1", "2024-01-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getSlopeTilesForField_rewritesUrl_whenPythonReturnsAbsolutePath() {
        mockAuthorizedSingleField("1", 1L, true);
        when(validationService.parseDate("2024-01-01"))
                .thenReturn(RequestValidationService.ValidationResult.ok(java.time.LocalDate.parse("2024-01-01")));
        Map<String, Object> pythonBody = Map.of(
                "url", "/slope/tiles/1/2024-01-01/SLOPE/{z}/{x}/{y}.png",
                "actual_date", "2024-01-01");
        when(slopePythonClient.getTilesForField(1L, "2024-01-01")).thenReturn(pythonBody);

        ResponseEntity<?> response = slopeController.getSlopeTilesForField("1", "2024-01-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("url")).isEqualTo("/slope/tiles/1/2024-01-01/SLOPE/{z}/{x}/{y}.png");
        assertThat(body.get("actual_date")).isEqualTo("2024-01-01");
    }

    @Test
    void getSlopeTrend_returnsBadGateway_whenPythonUnavailable() {
        mockAuthorizedTrendFields(List.of("1"), List.of(1L), "2023-01-01", "2023-03-01");
        when(slopePythonClient.getTrend(any(), any(), any()))
                .thenThrow(new ResourceAccessException("python down"));

        ResponseEntity<?> response = slopeController.getSlopeTrend(
                List.of("1"),
                "2023-01-01",
                "2023-03-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Python slope service unavailable"));
    }

    @Test
    void getSlopeTrend_returnsData_whenPythonResponds() {
        mockAuthorizedTrendFields(List.of("1"), List.of(1L), "2023-01-01", "2023-02-01");
        Map<String, Object> trendBody = Map.of(
                "labels", List.of("Jan", "Feb"),
                "data", List.of(2.1, 2.3)
        );
        when(slopePythonClient.getTrend(List.of(1L), "2023-01-01", "2023-02-01"))
                .thenReturn(trendBody);

        ResponseEntity<?> response = slopeController.getSlopeTrend(
                List.of("1"),
                "2023-01-01",
                "2023-02-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(trendBody);
    }

    @Test
    void getSlopeTile_returnsPng_whenPythonReturnsBytes() {
        mockAuthorizedSingleField("1", 1L, false);
        byte[] png = new byte[] {1, 2, 3};
        when(slopePythonClient.getTilePng("1", "2024-01-01", "SLOPE", 10, 20, 30))
                .thenReturn(png);

        ResponseEntity<byte[]> response = slopeController.getSlopeTile(
                "1", "2024-01-01", "SLOPE", 10, 20, 30);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    void getSlopeTilesForField_returnsUpstreamErrorBody_whenPythonReturnsHttpError() {
        mockAuthorizedSingleField("1", 1L, true);
        when(validationService.parseDate("2024-01-01"))
                .thenReturn(RequestValidationService.ValidationResult.ok(java.time.LocalDate.parse("2024-01-01")));
        HttpServerErrorException upstream = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "boom");
        when(slopePythonClient.getTilesForField(1L, "2024-01-01")).thenThrow(upstream);
        when(slopePythonClient.upstreamDetail(upstream)).thenReturn("{\"detail\":\"boom\"}");

        ResponseEntity<?> response = slopeController.getSlopeTilesForField("1", "2024-01-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "{\"detail\":\"boom\"}"));
    }

    private void mockAuthorizedSingleField(String rawFieldId, long fieldId, boolean parseDateForTiles) {
        when(validationService.requireOrganizationId())
                .thenReturn(RequestValidationService.ValidationResult.ok(10L));
        when(validationService.parseFieldId(rawFieldId))
                .thenReturn(RequestValidationService.ValidationResult.ok(fieldId));
        when(validationService.ensureFieldBelongsToOrganization(fieldId, 10L, false))
                .thenReturn(RequestValidationService.ValidationResult.ok(null));
        if (parseDateForTiles) {
            when(validationService.parseDate(any()))
                    .thenReturn(RequestValidationService.ValidationResult.ok(java.time.LocalDate.parse("2024-01-01")));
        }
    }

    private void mockAuthorizedTrendFields(
            List<String> rawFieldIds,
            List<Long> parsedFieldIds,
            String startDate,
            String endDate) {
        when(validationService.requireOrganizationId())
                .thenReturn(RequestValidationService.ValidationResult.ok(10L));
        when(validationService.parseFieldIds(rawFieldIds))
                .thenReturn(RequestValidationService.ValidationResult.ok(parsedFieldIds));
        for (Long fieldId : parsedFieldIds) {
            when(validationService.ensureFieldBelongsToOrganization(fieldId, 10L, true))
                    .thenReturn(RequestValidationService.ValidationResult.ok(null));
        }
        when(validationService.parseDateRange(startDate, endDate))
                .thenReturn(RequestValidationService.ValidationResult.ok(
                        new RequestValidationService.DateRange(
                                java.time.LocalDate.parse(startDate),
                                java.time.LocalDate.parse(endDate))));
    }
}

