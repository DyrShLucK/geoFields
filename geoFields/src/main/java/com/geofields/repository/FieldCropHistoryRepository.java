package com.geofields.repository;

import com.geofields.repository.row.CropOptionRow;
import com.geofields.repository.row.FieldCropHistoryRow;
import com.geofields.repository.row.FieldOptionRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FieldCropHistoryRepository {

    List<FieldOptionRow> listFieldsForOrganization(long organizationId);

    List<FieldOptionRow> listObsoleteFieldsForOrganization(long organizationId);

    List<CropOptionRow> listAllCrops();

    List<FieldCropHistoryRow> findHistoryByFieldAndOrganization(long fieldId, long organizationId);

    boolean historyBelongsToOrganization(long historyId, long organizationId);

    long countHistoryByFieldAndOrganization(long fieldId, long organizationId);

    Optional<Long> findFieldIdByHistoryId(long historyId, long organizationId);

    long insertHistory(
            long fieldId,
            long organizationId,
            long cropId,
            LocalDate sowingDate,
            LocalDate harvestDate,
            BigDecimal sownAreaHa,
            BigDecimal harvestAreaHa,
            BigDecimal actualYield,
            BigDecimal totalYield,
            BigDecimal plannedYield,
            BigDecimal forecastedYield,
            String sourceData,
            String sowingDetails,
            Integer cropYear);

    int updateHistory(
            long historyId,
            long organizationId,
            long cropId,
            LocalDate sowingDate,
            LocalDate harvestDate,
            BigDecimal sownAreaHa,
            BigDecimal harvestAreaHa,
            BigDecimal actualYield,
            BigDecimal totalYield,
            BigDecimal plannedYield,
            BigDecimal forecastedYield,
            String sourceData,
            String sowingDetails,
            Integer cropYear);

    int deleteHistory(long historyId, long organizationId);

    Optional<Long> findCropIdIfExists(long cropId);

    /** Ищет культуру по названию (без учёта регистра и краевых пробелов). */
    Optional<Long> findCropIdByName(String cropName);

    /** Добавляет культуру в справочник и возвращает её crop_id. */
    long insertCrop(String cropName);
}
