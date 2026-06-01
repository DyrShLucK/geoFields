package com.geofields.dto.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Запись истории посева из ответа стороннего сервиса.
 * Даты приходят строками (могут быть пустыми ""), числа — double; нормализация в {@code FieldImportSaveService}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportFieldHistoryItem(
        String cropName,
        String sowingDate,
        String harvestDate,
        Double sownAreaHa,
        Double harvestAreaHa,
        Double actualYield,
        Double totalYield,
        Double plannedYield,
        Double forecastedYield,
        String sourceData,
        String sowingDetails,
        Integer cropYear
) {
}
