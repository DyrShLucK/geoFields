package com.geofields.service;

import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.agronomist.FieldCropUpsertRequest;
import com.geofields.dto.agronomist.NewFieldIntakeRequest;
import com.geofields.repository.FieldCropHistoryRepository;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.row.FieldGeometryConflictRow;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldAddServiceTest {

    @Mock
    private FieldRepository fieldRepository;

    @Mock
    private FieldCropHistoryRepository fieldCropHistoryRepository;

    @Test
    void addField_createsFieldAndHistory_whenNoConflicts() {
        FieldAddService service = new FieldAddService(fieldRepository, fieldCropHistoryRepository);
        GeoFieldsUserDetails user = user();
        NewFieldIntakeRequest request = intakeRequest("New field", 77L, 2024);

        when(fieldCropHistoryRepository.findCropIdIfExists(77L)).thenReturn(Optional.of(77L));
        when(fieldRepository.findFieldsIntersectingGeometry(eq(10L), any(String.class), eq(null)))
                .thenReturn(List.of());
        when(fieldRepository.insertField(eq("New field"), any(String.class))).thenReturn(123L);
        when(fieldCropHistoryRepository.insertHistory(
                eq(123L), eq(10L), eq(77L), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(null), eq(null), eq(null), eq(null), eq(2024))).thenReturn(999L);

        ActionMessageResponse response = service.addField(request, user);

        assertThat(response.id()).isEqualTo(123L);
        assertThat(response.message()).isEqualTo("Поле создано");

        ArgumentCaptor<String> geometryCaptor = ArgumentCaptor.forClass(String.class);
        verify(fieldRepository).insertField(eq("New field"), geometryCaptor.capture());
        assertThat(geometryCaptor.getValue()).contains("\"type\":\"Polygon\"");

        verify(fieldRepository).saveFieldIntersections(10L, 123L, List.of());
    }

    @Test
    void addField_reportsConflictsCount_whenIntersectionsFound() {
        FieldAddService service = new FieldAddService(fieldRepository, fieldCropHistoryRepository);
        GeoFieldsUserDetails user = user();
        NewFieldIntakeRequest request = intakeRequest("Conflict field", 88L, 2025);

        List<FieldGeometryConflictRow> conflicts = List.of(
                new FieldGeometryConflictRow(31L, "F1"),
                new FieldGeometryConflictRow(32L, "F2"));

        when(fieldCropHistoryRepository.findCropIdIfExists(88L)).thenReturn(Optional.of(88L));
        when(fieldRepository.findFieldsIntersectingGeometry(eq(10L), any(String.class), eq(null)))
                .thenReturn(conflicts);
        when(fieldRepository.insertField(eq("Conflict field"), any(String.class))).thenReturn(456L);

        ActionMessageResponse response = service.addField(request, user);

        assertThat(response.id()).isEqualTo(456L);
        assertThat(response.message()).isEqualTo("Поле создано, пересечений найдено: 2");
        verify(fieldRepository).saveFieldIntersections(10L, 456L, List.of(31L, 32L));
    }

    @Test
    void addField_throwsWhenCropIsUnknown() {
        FieldAddService service = new FieldAddService(fieldRepository, fieldCropHistoryRepository);
        GeoFieldsUserDetails user = user();
        NewFieldIntakeRequest request = intakeRequest("Any field", 999L, 2024);

        when(fieldCropHistoryRepository.findCropIdIfExists(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addField(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестная культура");

        verify(fieldRepository, never()).insertField(any(String.class), any(String.class));
        verify(fieldCropHistoryRepository, never()).insertHistory(
                any(Long.class), any(Long.class), any(Long.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
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
