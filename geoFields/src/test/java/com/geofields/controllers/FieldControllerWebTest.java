package com.geofields.controllers;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.dto.FieldFeaturePropertiesDto;
import com.geofields.dto.GeoJsonGeometryDto;
import com.geofields.exception.ApiExceptionHandler;
import com.geofields.exception.FieldDataAccessException;
import com.geofields.service.FieldGeoJsonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldControllerWebTest {

    @Mock
    private FieldGeoJsonService fieldGeoJsonService;

    @InjectMocks
    private FieldController fieldController;

    private final ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();

    @Test
    void getFields_returnsFeatureCollection() throws Exception {
        GeoJsonGeometryDto geometry = new GeoJsonGeometryDto(
                "Polygon",
                "[[[37.0,55.0],[37.1,55.0],[37.1,55.1],[37.0,55.0]]]"
        );

        FieldFeatureCollectionDto response = new FieldFeatureCollectionDto(
                "FeatureCollection",
                List.of(
                        new FieldFeatureDto(
                                "Feature",
                                1L,
                                new FieldFeaturePropertiesDto(1L, "Field A", new BigDecimal("11.20"), Collections.emptyList()),
                                geometry
                        )
                )
        );

        when(fieldGeoJsonService.getFieldsAsFeatureCollection()).thenReturn(response);

        FieldFeatureCollectionDto result = fieldController.getFields();

        assertThat(result.type()).isEqualTo("FeatureCollection");
        assertThat(result.features()).hasSize(1);
        assertThat(result.features().getFirst().properties().name()).isEqualTo("Field A");
        assertThat(result.features().getFirst().geometry().type()).isEqualTo("Polygon");
    }

    @Test
    void handleFieldDataAccessException_returns500AndDetail() {
        FieldDataAccessException exception =
                new FieldDataAccessException("Ошибка чтения полей из БД", new RuntimeException("db"));

        ResponseEntity<?> response = apiExceptionHandler.handleFieldDataAccessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("Ошибка чтения полей из БД");
    }

    @Test
    void handleIllegalStateException_returns500AndDetail() {
        IllegalStateException exception = new IllegalStateException("Некорректная геометрия в БД");

        ResponseEntity<?> response = apiExceptionHandler.handleIllegalStateException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("Некорректная геометрия в БД");
    }
}
