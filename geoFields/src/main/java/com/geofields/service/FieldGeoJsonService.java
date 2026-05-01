package com.geofields.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.dto.FieldHistoryItemDto;
import com.geofields.dto.FieldFeaturePropertiesDto;
import com.geofields.dto.GeoJsonGeometryDto;
import com.geofields.exception.FieldDataAccessException;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.FieldHistoryRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FieldGeoJsonService {
    private static final Logger log = LoggerFactory.getLogger(FieldGeoJsonService.class);

    private final FieldRepository fieldRepository;
    private final StubAuthContextService stubAuthContextService;
    // Здесь хватает базового ObjectMapper только для разбора ST_AsGeoJSON.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FieldGeoJsonService(FieldRepository fieldRepository, StubAuthContextService stubAuthContextService) {
        this.fieldRepository = fieldRepository;
        this.stubAuthContextService = stubAuthContextService;
    }

    // Собираем итоговый ответ строго в формате OpenAPI: FeatureCollection.
    public FieldFeatureCollectionDto getFieldsAsFeatureCollection() {
        try {
            Long organizationId = stubAuthContextService.getCurrentOrganizationId();
            List<FieldHistoryRow> rows = fieldRepository.findAllFieldsWithHistory(organizationId);
            log.info("Repository returned {} joined rows for /get_fields, org={}", rows.size(), organizationId);
            List<FieldFeatureDto> features = buildFeatures(rows);
            log.info("Built {} GeoJSON features for /get_fields", features.size());

            return new FieldFeatureCollectionDto("FeatureCollection", features);
        } catch (DataAccessException ex) {
            throw new FieldDataAccessException("Ошибка чтения полей из БД: " + ex.getMessage(), ex);
        }
    }

    // Группируем плоские строки по полю и формируем полную историю.
    private List<FieldFeatureDto> buildFeatures(List<FieldHistoryRow> rows) {
        Map<Long, FieldAggregate> grouped = new LinkedHashMap<>();
        for (FieldHistoryRow row : rows) {
            // Если геометрия пустая, такую запись пропускаем, чтобы не уронить весь ответ.
            if (row.geometryJson() == null || row.geometryJson().isBlank()) {
                continue;
            }
            FieldAggregate aggregate = grouped.computeIfAbsent(row.fieldId(), id ->
                    new FieldAggregate(row, new ArrayList<>())
            );

            if (row.fieldCropId() != null) {
                aggregate.history().add(new FieldHistoryItemDto(
                        row.fieldCropId(),
                        row.cropId(),
                        row.cropName(),
                        row.sowingDate(),
                        row.harvestDate(),
                        row.sownAreaHa(),
                        row.harvestAreaHa(),
                        row.actualYield(),
                        row.totalYield(),
                        row.plannedYield(),
                        row.forecastedYield(),
                        row.sourceData(),
                        row.sowingDetails(),
                        row.cropYear(),
                        row.analyticsDate(),
                        row.ndviUrl(),
                        row.ndviCreatedAt()
                ));
            }
        }

        return grouped.values().stream()
                .map(this::toFeature)
                .toList();
    }

    // Преобразуем агрегат в GeoJSON Feature.
    private FieldFeatureDto toFeature(FieldAggregate aggregate) {
        JsonNode geometryNode = parseGeometry(aggregate.row().geometryJson());
        String geometryType = geometryNode.path("type").asText();
        // Превращаем JsonNode в обычные List/Map, чтобы сериализация дала чистый JSON.
        Object coordinates = objectMapper.convertValue(geometryNode.path("coordinates"), Object.class);

        return new FieldFeatureDto(
                "Feature",
                aggregate.row().fieldId(),
                new FieldFeaturePropertiesDto(
                        aggregate.row().fieldId(),
                        aggregate.row().fieldName(),
                        aggregate.row().fieldArea(),
                        aggregate.history()
                ),
                new GeoJsonGeometryDto(geometryType, coordinates)
        );
    }

    // Проверяем, что геометрия реально валидный JSON.
    private JsonNode parseGeometry(String geometryJson) {
        try {
            return objectMapper.readTree(geometryJson);
        } catch (Exception ex) {
            throw new IllegalStateException("Некорректная геометрия в БД", ex);
        }
    }

    private record FieldAggregate(FieldHistoryRow row, List<FieldHistoryItemDto> history) {
    }
}
