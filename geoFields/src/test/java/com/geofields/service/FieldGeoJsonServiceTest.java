package com.geofields.service;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.exception.FieldDataAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldGeoJsonServiceTest {

    @Mock
    private AuthContextService authContextService;
    @Mock
    private FieldGeoJsonQueryService fieldGeoJsonQueryService;

    @Test
    void getFieldsAsFeatureCollection_returnsOpenApiCompatibleGeoJson() throws Exception {
        FieldGeoJsonService service = new FieldGeoJsonService(authContextService, fieldGeoJsonQueryService);
        when(authContextService.getCurrentOrganizationId()).thenReturn(1L);
        when(fieldGeoJsonQueryService.loadForOrganization(1L)).thenReturn(
                new FieldFeatureCollectionDto(
                        "FeatureCollection",
                        List.of(
                                new FieldFeatureDto(
                                        "Feature",
                                        1L,
                                        new com.geofields.dto.FieldFeaturePropertiesDto(
                                                1L,
                                                "Field A",
                                                new BigDecimal("44.50"),
                                                true,
                                                List.of(
                                                        new com.geofields.dto.FieldHistoryItemDto(
                                                                101L,
                                                                7L,
                                                                "Wheat",
                                                                java.time.LocalDate.parse("2024-03-10"),
                                                                java.time.LocalDate.parse("2024-08-20"),
                                                                new BigDecimal("44.50"),
                                                                new BigDecimal("44.40"),
                                                                new BigDecimal("5.20"),
                                                                new BigDecimal("230.88"),
                                                                new BigDecimal("5.10"),
                                                                new BigDecimal("5.30"),
                                                                "satellite",
                                                                "spring sowing",
                                                                2024
                                                        )
                                                )
                                        ),
                                        new com.geofields.dto.GeoJsonGeometryDto(
                                                "Polygon",
                                                "[[[37.0,55.0],[37.1,55.0],[37.1,55.1],[37.0,55.0]]]"
                                        )
                                )
                        )
                )
        );

        FieldFeatureCollectionDto result = service.getFieldsAsFeatureCollection();
        FieldFeatureDto feature = result.features().getFirst();

        assertThat(result.type()).isEqualTo("FeatureCollection");
        assertThat(result.features()).hasSize(1);
        assertThat(feature.type()).isEqualTo("Feature");
        assertThat(feature.id()).isEqualTo(1L);
        assertThat(feature.properties().id()).isEqualTo(1L);
        assertThat(feature.properties().name()).isEqualTo("Field A");
        assertThat(feature.properties().area()).isEqualByComparingTo("44.50");
        assertThat(feature.properties().history()).hasSize(1);
        assertThat(feature.properties().history().getFirst().cropName()).isEqualTo("Wheat");
        assertThat(feature.geometry().type()).isEqualTo("Polygon");
        assertThat(feature.geometry().coordinates()).startsWith("[[[");
    }

    @Test
    void getFieldsAsFeatureCollection_wrapsDatabaseExceptions() {
        FieldGeoJsonService service = new FieldGeoJsonService(authContextService, fieldGeoJsonQueryService);
        when(authContextService.getCurrentOrganizationId()).thenReturn(1L);
        when(fieldGeoJsonQueryService.loadForOrganization(1L))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(service::getFieldsAsFeatureCollection)
                .isInstanceOf(FieldDataAccessException.class)
                .hasMessageContaining("Ошибка чтения полей из БД")
                .hasMessageContaining("db down");
    }

    @Test
    void getFieldsAsFeatureCollection_throwsWhenOrganizationMissingForUser() {
        FieldGeoJsonService service = new FieldGeoJsonService(authContextService, fieldGeoJsonQueryService);
        when(authContextService.getCurrentOrganizationId()).thenReturn(null);

        assertThatThrownBy(service::getFieldsAsFeatureCollection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("организац");
    }

    @Test
    void getFieldsAsFeatureCollection_propagatesIllegalStateFromQueryLayer() {
        FieldGeoJsonService service = new FieldGeoJsonService(authContextService, fieldGeoJsonQueryService);
        when(authContextService.getCurrentOrganizationId()).thenReturn(1L);
        when(fieldGeoJsonQueryService.loadForOrganization(1L))
                .thenThrow(new IllegalStateException("Некорректная геометрия в БД"));

        assertThatThrownBy(service::getFieldsAsFeatureCollection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Некорректная геометрия в БД");
    }
}
