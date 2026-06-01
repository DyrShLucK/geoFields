package com.geofields.service;

import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.agronomist.FieldCropUpsertRequest;
import com.geofields.dto.agronomist.NewFieldIntakeRequest;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldAddServiceTest {

    @Mock
    private FieldPersistenceService fieldPersistenceService;

    @Mock
    private FieldCropHistoryService fieldCropHistoryService;

    @Mock
    private GeoJsonSerializer geoJsonSerializer;

    @InjectMocks
    private FieldAddService service;

    @Test
    void addField_createsFieldAndHistory_whenNoConflicts() {
        GeoFieldsUserDetails user = user();
        NewFieldIntakeRequest request = intakeRequest("New field", 77L, 2024);
        String geometryJson = "{\"type\":\"Polygon\"}";

        when(geoJsonSerializer.writeGeometry(request.geometry(), request.fieldName())).thenReturn(geometryJson);
        when(fieldPersistenceService.createWithIntersections(10L, "New field", geometryJson))
                .thenReturn(new FieldPersistenceService.CreatedField(123L, 0));

        ActionMessageResponse response = service.addField(request, user);

        assertThat(response.id()).isEqualTo(123L);
        assertThat(response.message()).isEqualTo("Поле создано");
        verify(fieldCropHistoryService).insert(123L, 10L, request.history());
        verify(fieldPersistenceService).createWithIntersections(10L, "New field", geometryJson);
    }

    @Test
    void addField_reportsConflictsCount_whenIntersectionsFound() {
        GeoFieldsUserDetails user = user();
        NewFieldIntakeRequest request = intakeRequest("Conflict field", 88L, 2025);
        String geometryJson = "{\"type\":\"Polygon\"}";

        when(geoJsonSerializer.writeGeometry(request.geometry(), request.fieldName())).thenReturn(geometryJson);
        when(fieldPersistenceService.createWithIntersections(10L, "Conflict field", geometryJson))
                .thenReturn(new FieldPersistenceService.CreatedField(456L, 2));

        ActionMessageResponse response = service.addField(request, user);

        assertThat(response.id()).isEqualTo(456L);
        assertThat(response.message()).isEqualTo("Поле создано, пересечений найдено: 2");
    }

    @Test
    void addField_propagatesUnknownCropFromHistoryService() {
        GeoFieldsUserDetails user = user();
        NewFieldIntakeRequest request = intakeRequest("Any field", 999L, 2024);
        String geometryJson = "{\"type\":\"Polygon\"}";

        when(geoJsonSerializer.writeGeometry(request.geometry(), request.fieldName())).thenReturn(geometryJson);
        when(fieldPersistenceService.createWithIntersections(eq(10L), eq("Any field"), eq(geometryJson)))
                .thenReturn(new FieldPersistenceService.CreatedField(123L, 0));
        when(fieldCropHistoryService.insert(123L, 10L, request.history()))
                .thenThrow(new IllegalArgumentException("Неизвестная культура (crop_id)"));

        assertThatThrownBy(() -> service.addField(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестная культура");
    }

    private static GeoFieldsUserDetails user() {
        return new GeoFieldsUserDetails(
                501L,
                10L,
                "agronomist",
                "hash",
                UserRole.AGRONOMIST,
                true,
                "Ivanov",
                "Ivan",
                "Ivanovich");
    }

    private static NewFieldIntakeRequest intakeRequest(String fieldName, Long cropId, Integer cropYear) {
        return new NewFieldIntakeRequest(
                fieldName,
                new NewFieldIntakeRequest.GeoJsonGeometryInput(
                        "Polygon",
                        List.of(List.of(List.of(37.0, 55.0), List.of(37.1, 55.0), List.of(37.0, 55.0)))),
                new FieldCropUpsertRequest(
                        cropId,
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
                        cropYear));
    }
}
