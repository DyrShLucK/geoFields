package com.geofields.service;

import com.geofields.dto.imports.ImportCropItem;
import com.geofields.dto.imports.ImportFieldFeature;
import com.geofields.dto.imports.ImportFieldFeatureCollection;
import com.geofields.dto.imports.ImportFieldHistoryItem;
import com.geofields.dto.imports.ImportFieldProperties;
import com.geofields.dto.imports.ShapefileImportCommitResponse;
import com.geofields.dto.imports.ShapefileImportResponse;
import com.geofields.repository.FieldCropHistoryRepository;
import com.geofields.security.GeoFieldsUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldImportSaveServiceTest {

    @Mock
    private FieldPersistenceService fieldPersistenceService;

    @Mock
    private FieldCropHistoryService fieldCropHistoryService;

    @Mock
    private FieldCropHistoryRepository fieldCropHistoryRepository;

    @Mock
    private GeoJsonSerializer geoJsonSerializer;

    private FieldImportSaveService service;

    @BeforeEach
    void setUp() {
        CropLookupService cropLookupService = new CropLookupService(fieldCropHistoryRepository);
        service = new FieldImportSaveService(
                fieldPersistenceService,
                fieldCropHistoryService,
                cropLookupService,
                geoJsonSerializer);
    }

    @Test
    void save_rejectsEmptyPayload() {
        assertThatThrownBy(() -> service.save(null, ImportTestFixtures.agronomistUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Нет полей");
    }

    @Test
    void save_rejectsFieldWithoutName() {
        ShapefileImportResponse payload = new ShapefileImportResponse(
                new ImportFieldFeatureCollection("FeatureCollection", List.of(
                        new ImportFieldFeature(
                                "Feature",
                                new ImportFieldProperties("  ", 1.0, true, List.of()),
                                ImportTestFixtures.polygonGeometry()))),
                List.of());

        assertThatThrownBy(() -> service.save(payload, ImportTestFixtures.agronomistUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("название");
    }

    @Test
    void save_persistsFieldAndHistory() {
        GeoFieldsUserDetails user = ImportTestFixtures.agronomistUser();
        ShapefileImportResponse payload = ImportTestFixtures.sampleImportResponse();
        String geometryJson = ImportTestFixtures.sampleGeometryJson();

        when(geoJsonSerializer.writeGeometry(payload.fields().features().getFirst().geometry(), " Поле А "))
                .thenReturn(geometryJson);
        when(fieldCropHistoryRepository.findCropIdByName("Пшеница")).thenReturn(Optional.of(7L));
        when(fieldPersistenceService.createWithIntersections(10L, "Поле А", geometryJson))
                .thenReturn(new FieldPersistenceService.CreatedField(100L, 1));

        ShapefileImportCommitResponse result = service.save(payload, user);

        assertThat(result.savedFields()).isEqualTo(1);
        assertThat(result.savedHistories()).isEqualTo(1);
        assertThat(result.createdCrops()).isZero();
        assertThat(result.message()).contains("1");

        verify(fieldPersistenceService).createWithIntersections(10L, "Поле А", geometryJson);
        verify(fieldCropHistoryService).insertImportRecord(
                eq(100L),
                eq(10L),
                eq(7L),
                eq(payload.fields().features().getFirst().properties().history().getFirst()));
        verify(fieldCropHistoryRepository, never()).insertCrop(anyString());
    }

    @Test
    void save_createsMissingCropsFromDictionaryAndHistory() {
        GeoFieldsUserDetails user = ImportTestFixtures.agronomistUser();
        ImportFieldHistoryItem history = new ImportFieldHistoryItem(
                "Овёс", null, null, null, null, null, null, null, null, null, null, 2023);
        ShapefileImportResponse payload = new ShapefileImportResponse(
                new ImportFieldFeatureCollection("FeatureCollection", List.of(
                        new ImportFieldFeature(
                                "Feature",
                                new ImportFieldProperties("North-1", 5.0, true, List.of(history)),
                                ImportTestFixtures.polygonGeometry()))),
                List.of(new ImportCropItem("Овёс")));

        when(geoJsonSerializer.writeGeometry(any(), eq("North-1"))).thenReturn(ImportTestFixtures.sampleGeometryJson());
        when(fieldCropHistoryRepository.findCropIdByName("Овёс")).thenReturn(Optional.empty());
        when(fieldCropHistoryRepository.insertCrop("Овёс")).thenReturn(42L);
        when(fieldPersistenceService.createWithIntersections(anyLong(), anyString(), anyString()))
                .thenReturn(new FieldPersistenceService.CreatedField(200L, 0));

        ShapefileImportCommitResponse result = service.save(payload, user);

        assertThat(result.savedFields()).isEqualTo(1);
        assertThat(result.savedHistories()).isEqualTo(1);
        assertThat(result.createdCrops()).isEqualTo(1);
        verify(fieldCropHistoryRepository).insertCrop("Овёс");
    }

    @Test
    void save_skipsHistoryWhenCropNameOrYearMissing() {
        GeoFieldsUserDetails user = ImportTestFixtures.agronomistUser();
        ImportFieldHistoryItem withoutYear = new ImportFieldHistoryItem(
                "Рапс", null, null, null, null, null, null, null, null, null, null, null);
        ImportFieldHistoryItem withoutCrop = new ImportFieldHistoryItem(
                "", null, null, null, null, null, null, null, null, null, null, 2022);
        ShapefileImportResponse payload = new ShapefileImportResponse(
                new ImportFieldFeatureCollection("FeatureCollection", List.of(
                        new ImportFieldFeature(
                                "Feature",
                                new ImportFieldProperties("Skip hist", 1.0, true, List.of(withoutYear, withoutCrop)),
                                ImportTestFixtures.polygonGeometry()))),
                List.of());

        when(geoJsonSerializer.writeGeometry(any(), eq("Skip hist"))).thenReturn(ImportTestFixtures.sampleGeometryJson());
        when(fieldPersistenceService.createWithIntersections(10L, "Skip hist", ImportTestFixtures.sampleGeometryJson()))
                .thenReturn(new FieldPersistenceService.CreatedField(300L, 0));

        ShapefileImportCommitResponse result = service.save(payload, user);

        assertThat(result.savedFields()).isEqualTo(1);
        assertThat(result.savedHistories()).isZero();
        verify(fieldCropHistoryService, never()).insertImportRecord(anyLong(), anyLong(), anyLong(), any());
    }
}
