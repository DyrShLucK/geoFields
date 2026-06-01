package com.geofields.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.dto.FieldFeaturePropertiesDto;
import com.geofields.dto.FieldHistoryItemDto;
import com.geofields.dto.GeoJsonGeometryDto;
import com.geofields.repository.row.FieldHistoryRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FieldGeoJsonAssembler {

    private final ObjectMapper objectMapper;

    public FieldGeoJsonAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FieldFeatureCollectionDto toFeatureCollection(List<FieldHistoryRow> rows) {
        List<FieldFeatureDto> features = buildFeatures(rows);
        return new FieldFeatureCollectionDto("FeatureCollection", features);
    }

    private List<FieldFeatureDto> buildFeatures(List<FieldHistoryRow> rows) {
        Map<Long, FieldAggregate> grouped = new LinkedHashMap<>();
        for (FieldHistoryRow row : rows) {
            if (row.geometryJson() == null || row.geometryJson().isBlank()) {
                continue;
            }
            FieldAggregate aggregate = grouped.computeIfAbsent(row.fieldId(), id ->
                    new FieldAggregate(row, new ArrayList<>())
            );

            if (row.fieldCropId() != null) {
                aggregate.history().add(toHistoryItem(row));
            }
        }

        return grouped.values().stream()
                .map(this::toFeature)
                .toList();
    }

    private FieldFeatureDto toFeature(FieldAggregate aggregate) {
        JsonNode geometryNode = parseGeometry(aggregate.row().geometryJson());
        String geometryType = geometryNode.path("type").asText();
        JsonNode coordNode = geometryNode.get("coordinates");
        if (coordNode == null || coordNode.isNull() || coordNode.isMissingNode()) {
            throw new IllegalStateException("В геометрии поля " + aggregate.row().fieldId() + " нет coordinates");
        }
        String coordinatesJson = writeCoordinatesJson(coordNode);

        return new FieldFeatureDto(
                "Feature",
                aggregate.row().fieldId(),
                new FieldFeaturePropertiesDto(
                        aggregate.row().fieldId(),
                        aggregate.row().fieldName(),
                        aggregate.row().fieldArea(),
                        aggregate.row().active(),
                        aggregate.history()
                ),
                new GeoJsonGeometryDto(geometryType, coordinatesJson)
        );
    }

    private FieldHistoryItemDto toHistoryItem(FieldHistoryRow row) {
        return new FieldHistoryItemDto(
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
        );
    }

    private String writeCoordinatesJson(JsonNode coordNode) {
        try {
            return objectMapper.writeValueAsString(coordNode);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать coordinates в JSON", e);
        }
    }

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
