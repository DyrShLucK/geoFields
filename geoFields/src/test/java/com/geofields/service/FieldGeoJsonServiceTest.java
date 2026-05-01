package com.geofields.service;

import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.exception.FieldDataAccessException;
import com.geofields.model.Fields;
import com.geofields.repository.FieldRepository;
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

    @Test
    void getFieldsAsFeatureCollection_returnsOpenApiCompatibleGeoJson() {
        FieldGeoJsonService service = new FieldGeoJsonService(fieldRepository);
        // Обычный успешный сценарий: одна запись из БД превращается в один Feature.
        when(fieldRepository.findAllFields()).thenReturn(List.of(
                new Fields(
                        1L,
                        "Field A",
                        new BigDecimal("44.50"),
                        "{\"type\":\"Polygon\",\"coordinates\":[[[37.0,55.0],[37.1,55.0],[37.1,55.1],[37.0,55.0]]]}"
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
        assertThat(feature.geometry().type()).isEqualTo("Polygon");
        assertThat(feature.geometry().coordinates()).isInstanceOf(List.class);
    }

    @Test
    void getFieldsAsFeatureCollection_wrapsDatabaseExceptions() {
        FieldGeoJsonService service = new FieldGeoJsonService(fieldRepository);
        // Ошибка базы должна превратиться в нашу доменную ошибку сервиса.
        when(fieldRepository.findAllFields()).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(service::getFieldsAsFeatureCollection)
                .isInstanceOf(FieldDataAccessException.class)
                .hasMessage("Ошибка чтения полей из БД");
    }

    @Test
    void getFieldsAsFeatureCollection_throwsWhenGeometryJsonIsInvalid() {
        FieldGeoJsonService service = new FieldGeoJsonService(fieldRepository);
        // Если в geom невалидный JSON — сервис должен упасть с понятным сообщением.
        when(fieldRepository.findAllFields()).thenReturn(List.of(
                new Fields(1L, "Broken field", new BigDecimal("1.00"), "not-a-json")
        ));

        assertThatThrownBy(service::getFieldsAsFeatureCollection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Некорректная геометрия в БД");
    }
}
