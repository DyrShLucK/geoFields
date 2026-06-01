package com.geofields.service;

import com.geofields.repository.FieldCropHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropLookupServiceTest {

    @Mock
    private FieldCropHistoryRepository fieldCropHistoryRepository;

    @InjectMocks
    private CropLookupService cropLookupService;

    @Test
    void resolve_returnsNullForBlankName() {
        CropLookupService.CropResolveSession session = cropLookupService.openSession();

        assertThat(session.resolve(null)).isNull();
        assertThat(session.resolve("   ")).isNull();
        assertThat(session.createdCount()).isZero();
    }

    @Test
    void resolve_usesExistingCropFromRepository() {
        when(fieldCropHistoryRepository.findCropIdByName("Пшеница")).thenReturn(Optional.of(11L));

        CropLookupService.CropResolveSession session = cropLookupService.openSession();

        assertThat(session.resolve("Пшеница")).isEqualTo(11L);
        assertThat(session.resolve("  пшеница ")).isEqualTo(11L);
        assertThat(session.createdCount()).isZero();
        verify(fieldCropHistoryRepository, times(1)).findCropIdByName("Пшеница");
    }

    @Test
    void resolve_insertsCropWhenMissing() {
        when(fieldCropHistoryRepository.findCropIdByName("Овёс")).thenReturn(Optional.empty());
        when(fieldCropHistoryRepository.insertCrop("Овёс")).thenReturn(22L);

        CropLookupService.CropResolveSession session = cropLookupService.openSession();

        assertThat(session.resolve("Овёс")).isEqualTo(22L);
        assertThat(session.createdCount()).isEqualTo(1);
        verify(fieldCropHistoryRepository).insertCrop("Овёс");
    }
}
