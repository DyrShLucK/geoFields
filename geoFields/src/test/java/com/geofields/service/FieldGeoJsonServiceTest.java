package com.geofields.service;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.exception.FieldDataAccessException;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.FieldHistoryRow;
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
    private FieldRepository fieldRepository;
    @Mock
    private StubAuthContextService stubAuthContextService;

    @Test
    void getFieldsAsFeatureCollection_returnsOpenApiCompatibleGeoJson() {
        FieldGeoJsonService service = new FieldGeoJsonService(fieldRepository, stubAuthContextService);
        when(stubAuthContextService.getCurrentOrganizationId()).thenReturn(1L);
        // Обычный успешный сценарий: одна запись из БД превращается в один Feature.
        when(fieldRepository.findAllFieldsWithHistory(1L)).thenReturn(List.of(
                new FieldHistoryRow(
                        1L,
                        "Field A",
                        new BigDecimal("44.50"),
                        "{\"type\":\"Polygon\",\"coordinates\":[[[37.0,55.0],[37.1,55.0],[37.1,55.1],[37.0,55.0]]]}",
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
                        2024,
                        java.time.LocalDate.parse("2024-06-15"),
                        "https://tiles.example/{z}/{x}/{y}",
                        java.time.LocalDateTime.parse("2024-06-15T12:00:00")
                )
        ));

        FieldFeatureCollectionDto result = service.getFieldsAsFeatureCollection();
        FieldFeatureDto feature = result.features().getFirst();

        // Проверяем структуру ответа и ключевые поля OpenAPI.
        assertThat(result.type()).isEqualTo("FeatureCollection");
        assertThat(result.features()).hasSize(1);
        assertThat(feature.type()).isEqualTo("Feature");
        assertThat(feature.id()).isEqualTo(1L);
        assertThat(feature.properties().id()).isEqualTo(1L);
        assertThat(feature.properties().name()).isEqualTo("Field A");
        assertThat(feature.properties().area()).isEqualByComparingTo("44.50");
        assertThat(feature.properties().history()).hasSize(1);
        assertThat(feature.properties().history().getFirst().cropName()).isEqualTo("Wheat");
        assertThat(feature.properties().history().getFirst().ndviUrl()).contains("tiles.example");
        assertThat(feature.geometry().type()).isEqualTo("Polygon");
        assertThat(feature.geometry().coordinates()).isInstanceOf(List.class);
    }

    @Test
    void getFieldsAsFeatureCollection_wrapsDatabaseExceptions() {
        FieldGeoJsonService service = new FieldGeoJsonService(fieldRepository, stubAuthContextService);
        when(stubAuthContextService.getCurrentOrganizationId()).thenReturn(1L);
        // Ошибка базы должна превратиться в нашу доменную ошибку сервиса.
        when(fieldRepository.findAllFieldsWithHistory(1L)).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(service::getFieldsAsFeatureCollection)
                .isInstanceOf(FieldDataAccessException.class)
                .hasMessageContaining("Ошибка чтения полей из БД")
                .hasMessageContaining("db down");
    }

    @Test
    void getFieldsAsFeatureCollection_throwsWhenGeometryJsonIsInvalid() {
        FieldGeoJsonService service = new FieldGeoJsonService(fieldRepository, stubAuthContextService);
        when(stubAuthContextService.getCurrentOrganizationId()).thenReturn(1L);
        // Если в geom невалидный JSON — сервис должен упасть с понятным сообщением.
        when(fieldRepository.findAllFieldsWithHistory(1L)).thenReturn(List.of(
                new FieldHistoryRow(
                        1L,
                        "Broken field",
                        new BigDecimal("1.00"),
                        "not-a-json",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ));

        assertThatThrownBy(service::getFieldsAsFeatureCollection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Некорректная геометрия в БД");
    }
}
