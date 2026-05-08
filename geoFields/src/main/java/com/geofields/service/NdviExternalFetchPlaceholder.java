package com.geofields.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geofields.dto.FieldFeatureCollectionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Заглушка исходящего запроса NDVI, когда данных в БД нет на дату. */
@Component
public class NdviExternalFetchPlaceholder {

    private static final Logger log = LoggerFactory.getLogger(NdviExternalFetchPlaceholder.class);

    private final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    public void requestTileForField(
            long organizationId,
            long fieldId,
            LocalDate date,
            FieldFeatureCollectionDto fieldsGeoJson) {
        log.info(
                "NDVI external API (stub): tile for field — organizationId={} fieldId={} date={} fieldsGeoJson={}",
                organizationId,
                fieldId,
                date,
                serializeGeoJson(fieldsGeoJson));
    }

    public void requestTileForFields(
            long organizationId,
            List<Long> fieldIds,
            LocalDate date,
            FieldFeatureCollectionDto fieldsGeoJson) {
        log.info(
                "NDVI external API (stub): tile for fields — organizationId={} fieldIds={} date={} fieldsGeoJson={}",
                organizationId,
                fieldIds,
                date,
                serializeGeoJson(fieldsGeoJson));
    }

    public void requestValueForPoint(
            long organizationId,
            double latitude,
            double longitude,
            LocalDate date,
            Long fieldId,
            FieldFeatureCollectionDto fieldsGeoJson) {
        log.info(
                "NDVI external API (stub): point value — organizationId={} lat={} lon={} date={} fieldId={} fieldsGeoJson={}",
                organizationId,
                latitude,
                longitude,
                date,
                fieldId,
                serializeGeoJson(fieldsGeoJson));
    }

    private String serializeGeoJson(FieldFeatureCollectionDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать GeoJSON для исходящего NDVI", e);
            return "\"<serialization_error>\"";
        }
    }
}
