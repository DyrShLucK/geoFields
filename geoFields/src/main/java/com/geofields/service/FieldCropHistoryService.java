package com.geofields.service;

import com.geofields.dto.agronomist.FieldCropUpsertRequest;
import com.geofields.dto.imports.ImportFieldHistoryItem;
import com.geofields.repository.FieldCropHistoryRepository;
import org.springframework.stereotype.Service;

/** Запись и проверка истории посевов без дублирования параметров insert/update. */
@Service
public class FieldCropHistoryService {

    private final FieldCropHistoryRepository fieldCropHistoryRepository;

    public FieldCropHistoryService(FieldCropHistoryRepository fieldCropHistoryRepository) {
        this.fieldCropHistoryRepository = fieldCropHistoryRepository;
    }

    public void requireKnownCrop(long cropId) {
        if (fieldCropHistoryRepository.findCropIdIfExists(cropId).isEmpty()) {
            throw new IllegalArgumentException("Неизвестная культура (crop_id)");
        }
    }

    public long insert(long fieldId, long organizationId, FieldCropUpsertRequest request) {
        requireKnownCrop(request.cropId());
        return fieldCropHistoryRepository.insertHistory(
                fieldId,
                organizationId,
                request.cropId(),
                request.sowingDate(),
                request.harvestDate(),
                request.sownAreaHa(),
                request.harvestAreaHa(),
                request.actualYield(),
                request.totalYield(),
                request.plannedYield(),
                request.forecastedYield(),
                request.sourceData(),
                request.sowingDetails(),
                request.cropYear());
    }

    public int update(long historyId, long organizationId, FieldCropUpsertRequest request) {
        requireKnownCrop(request.cropId());
        return fieldCropHistoryRepository.updateHistory(
                historyId,
                organizationId,
                request.cropId(),
                request.sowingDate(),
                request.harvestDate(),
                request.sownAreaHa(),
                request.harvestAreaHa(),
                request.actualYield(),
                request.totalYield(),
                request.plannedYield(),
                request.forecastedYield(),
                request.sourceData(),
                request.sowingDetails(),
                request.cropYear());
    }

    public long insertImportRecord(long fieldId, long organizationId, long cropId, ImportFieldHistoryItem item) {
        return fieldCropHistoryRepository.insertHistory(
                fieldId,
                organizationId,
                cropId,
                ImportValueConverter.parseDate(item.sowingDate()),
                ImportValueConverter.parseDate(item.harvestDate()),
                ImportValueConverter.toBigDecimal(item.sownAreaHa()),
                ImportValueConverter.toBigDecimal(item.harvestAreaHa()),
                ImportValueConverter.toBigDecimal(item.actualYield()),
                ImportValueConverter.toBigDecimal(item.totalYield()),
                ImportValueConverter.toBigDecimal(item.plannedYield()),
                ImportValueConverter.toBigDecimal(item.forecastedYield()),
                item.sourceData(),
                item.sowingDetails(),
                item.cropYear());
    }
}
