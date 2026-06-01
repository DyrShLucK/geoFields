package com.geofields.service;

import com.geofields.dto.imports.ImportCropItem;
import com.geofields.dto.imports.ImportFieldFeature;
import com.geofields.dto.imports.ImportFieldHistoryItem;
import com.geofields.dto.imports.ImportFieldProperties;
import com.geofields.dto.imports.ShapefileImportCommitResponse;
import com.geofields.dto.imports.ShapefileImportResponse;
import com.geofields.security.GeoFieldsUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сохраняет в БД поля, проверенные пользователем после импорта.
 * Никаких обращений к внешним сервисам — только запись в базу.
 */
@Service
public class FieldImportSaveService {

    private static final Logger log = LoggerFactory.getLogger(FieldImportSaveService.class);

    private final FieldPersistenceService fieldPersistenceService;
    private final FieldCropHistoryService fieldCropHistoryService;
    private final CropLookupService cropLookupService;
    private final GeoJsonSerializer geoJsonSerializer;

    public FieldImportSaveService(
            FieldPersistenceService fieldPersistenceService,
            FieldCropHistoryService fieldCropHistoryService,
            CropLookupService cropLookupService,
            GeoJsonSerializer geoJsonSerializer) {
        this.fieldPersistenceService = fieldPersistenceService;
        this.fieldCropHistoryService = fieldCropHistoryService;
        this.cropLookupService = cropLookupService;
        this.geoJsonSerializer = geoJsonSerializer;
    }

    @Transactional
    public ShapefileImportCommitResponse save(ShapefileImportResponse payload, GeoFieldsUserDetails user) {
        if (payload == null || payload.fields() == null || payload.fields().features() == null
                || payload.fields().features().isEmpty()) {
            throw new IllegalArgumentException("Нет полей для сохранения");
        }
        long organizationId = user.getOrganizationId();
        CropLookupService.CropResolveSession crops = cropLookupService.openSession();
        preCreateDictionaryCrops(payload.crops(), crops);

        int savedFields = 0;
        int savedHistories = 0;

        for (ImportFieldFeature feature : payload.fields().features()) {
            ImportFieldProperties props = requireProperties(feature);
            String geometryGeoJson = geoJsonSerializer.writeGeometry(feature.geometry(), props.name());
            FieldPersistenceService.CreatedField created = fieldPersistenceService.createWithIntersections(
                    organizationId,
                    props.name().trim(),
                    geometryGeoJson);
            savedFields++;
            savedHistories += saveHistoryForField(created.fieldId(), organizationId, props.history(), crops);
        }

        log.info("Импорт сохранён: полей={}, историй={}, новых культур={}",
                savedFields, savedHistories, crops.createdCount());
        return new ShapefileImportCommitResponse(
                savedFields,
                savedHistories,
                crops.createdCount(),
                "Импортировано полей: " + savedFields);
    }

    private static ImportFieldProperties requireProperties(ImportFieldFeature feature) {
        ImportFieldProperties props = feature.properties();
        if (props == null || props.name() == null || props.name().isBlank()) {
            throw new IllegalArgumentException("У одного из полей отсутствует название");
        }
        return props;
    }

    private void preCreateDictionaryCrops(List<ImportCropItem> dictionary, CropLookupService.CropResolveSession crops) {
        if (dictionary == null) {
            return;
        }
        for (ImportCropItem item : dictionary) {
            crops.resolve(item.cropName());
        }
    }

    private int saveHistoryForField(
            long fieldId,
            long organizationId,
            List<ImportFieldHistoryItem> history,
            CropLookupService.CropResolveSession crops) {
        if (history == null) {
            return 0;
        }
        int saved = 0;
        for (ImportFieldHistoryItem item : history) {
            Long cropId = crops.resolve(item.cropName());
            if (cropId == null || item.cropYear() == null) {
                continue;
            }
            fieldCropHistoryService.insertImportRecord(fieldId, organizationId, cropId, item);
            saved++;
        }
        return saved;
    }
}
