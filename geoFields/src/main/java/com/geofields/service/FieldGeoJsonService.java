package com.geofields.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofields.dto.FieldFeatureCollectionDto;
import com.geofields.dto.FieldFeatureDto;
import com.geofields.dto.FieldFeaturePropertiesDto;
import com.geofields.dto.GeoJsonGeometryDto;
import com.geofields.exception.FieldDataAccessException;
import com.geofields.model.Fields;
import com.geofields.repository.FieldRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FieldGeoJsonService {

    private final FieldRepository fieldRepository;
    // Здесь хватает базового ObjectMapper только для разбора ST_AsGeoJSON.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FieldGeoJsonService(FieldRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    // Собираем итоговый ответ строго в формате OpenAPI: FeatureCollection.
    public FieldFeatureCollectionDto getFieldsAsFeatureCollection() {
        try {
            List<FieldFeatureDto> features = fieldRepository.findAllFields()
                    .stream()
                    .map(this::toFeature)
                    .toList();

            return new FieldFeatureCollectionDto("FeatureCollection", features);
        } catch (DataAccessException ex) {
            throw new FieldDataAccessException("Ошибка чтения полей из БД", ex);
        }
    }

    // Преобразуем одну строку из БД в один GeoJSON Feature.
    private FieldFeatureDto toFeature(Fields row) {
        JsonNode geometryNode = parseGeometry(row.getField_geometry());
        String geometryType = geometryNode.path("type").asText();
        // Превращаем JsonNode в обычные List/Map, чтобы сериализация дала чистый JSON.
        Object coordinates = objectMapper.convertValue(geometryNode.path("coordinates"), Object.class);

        return new FieldFeatureDto(
                "Feature",
                row.getId(),
                new FieldFeaturePropertiesDto(row.getId(), row.getField_name(), row.getField_area()),
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
}
