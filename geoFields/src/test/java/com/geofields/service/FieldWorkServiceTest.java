package com.geofields.service;

import com.geofields.dto.fieldwork.FieldWorkCreateRequest;
import com.geofields.dto.fieldwork.FieldWorkUpdateRequest;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.FieldWorkRepository;
import com.geofields.repository.row.FieldWorkItemRow;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldWorkServiceTest {

    @Mock
    private FieldWorkRepository fieldWorkRepository;
    @Mock
    private FieldRepository fieldRepository;
    @InjectMocks
    private FieldWorkService fieldWorkService;

    private final GeoFieldsUserDetails user = new GeoFieldsUserDetails(
            5L, 1L, "agro", "hash", UserRole.AGRONOMIST, true, "Иванов", "Иван", "");

    @Test
    void create_recordsInitialStatusHistory() {
        when(fieldRepository.fieldBelongsToOrganization(9L, 1L)).thenReturn(true);
        when(fieldWorkRepository.categoryExists("FERTILIZATION")).thenReturn(true);
        when(fieldWorkRepository.insertOperation(
                eq(9L), eq(1L), eq(5L), any(), eq("FERTILIZATION"), eq("PLANNED"), any()))
                .thenReturn(100L);

        var request = new FieldWorkCreateRequest(
                "Внесение NPK",
                "FERTILIZATION",
                LocalDateTime.parse("2026-05-10T08:00:00"),
                null,
                "Старт сезона");

        var response = fieldWorkService.create(9L, request, user);

        assertThat(response.id()).isEqualTo(100L);
        verify(fieldWorkRepository).insertStatusHistory(100L, "PLANNED", 5L, "Старт сезона");
    }

    @Test
    void update_appendsHistoryWhenStatusChanges() {
        FieldWorkItemRow existing = new FieldWorkItemRow(
                100L, 9L, 1L, "Op", "FERTILIZATION", "Удобрения",
                "PLANNED", "Запланировано",
                LocalDateTime.parse("2026-05-10T08:00:00"),
                5L,
                LocalDateTime.now(),
                LocalDateTime.now());
        when(fieldWorkRepository.findById(100L, 1L)).thenReturn(Optional.of(existing));
        when(fieldWorkRepository.categoryExists("FERTILIZATION")).thenReturn(true);
        when(fieldWorkRepository.statusExists("IN_PROGRESS")).thenReturn(true);
        when(fieldWorkRepository.updateOperation(eq(100L), eq(1L), any(), eq("FERTILIZATION"), eq("IN_PROGRESS"), any()))
                .thenReturn(1);

        var request = new FieldWorkUpdateRequest(
                "Внесение NPK",
                "FERTILIZATION",
                LocalDateTime.parse("2026-05-10T10:00:00"),
                "IN_PROGRESS",
                "Пошло в работу");

        fieldWorkService.update(100L, request, user);

        verify(fieldWorkRepository).insertStatusHistory(100L, "IN_PROGRESS", 5L, "Пошло в работу");
    }

    @Test
    void create_rejectsUnknownCategory() {
        when(fieldRepository.fieldBelongsToOrganization(9L, 1L)).thenReturn(true);
        when(fieldWorkRepository.statusExists("PLANNED")).thenReturn(true);
        when(fieldWorkRepository.categoryExists("UNKNOWN")).thenReturn(false);

        var request = new FieldWorkCreateRequest(
                "Test",
                "UNKNOWN",
                LocalDateTime.now(),
                "PLANNED",
                null);

        assertThatThrownBy(() -> fieldWorkService.create(9L, request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("категория");
    }
}
