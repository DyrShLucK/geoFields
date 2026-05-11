package com.geofields.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.agronomist.NewFieldIntakeRequest;
import com.geofields.repository.FieldCropHistoryRepository;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.row.FieldGeometryConflictRow;
import com.geofields.security.GeoFieldsUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FieldAddService {

    private final FieldRepository fieldRepository;
    private final FieldCropHistoryRepository fieldCropHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FieldAddService(
            FieldRepository fieldRepository,
            FieldCropHistoryRepository fieldCropHistoryRepository) {
        this.fieldRepository = fieldRepository;
        this.fieldCropHistoryRepository = fieldCropHistoryRepository;
    }

    @Transactional
    public ActionMessageResponse addField(NewFieldIntakeRequest request, GeoFieldsUserDetails user) {
        long organizationId = user.getOrganizationId();
        validateCropExists(request.history().cropId());

        String geometryGeoJson = toGeometryGeoJson(request);
        List<FieldGeometryConflictRow> conflicts =
                fieldRepository.findFieldsIntersectingGeometry(organizationId, geometryGeoJson, null);

        long fieldId = fieldRepository.insertField(request.fieldName(), geometryGeoJson);
        fieldCropHistoryRepository.insertHistory(
                fieldId,
                organizationId,
                request.history().cropId(),
                request.history().sowingDate(),
                request.history().harvestDate(),
                request.history().sownAreaHa(),
                request.history().harvestAreaHa(),
                request.history().actualYield(),
                request.history().totalYield(),
                request.history().plannedYield(),
                request.history().forecastedYield(),
                request.history().sourceData(),
                request.history().sowingDetails(),
                request.history().cropYear());

        fieldRepository.saveFieldIntersections(
                organizationId,
                fieldId,
                conflicts.stream().map(FieldGeometryConflictRow::fieldId).toList());

        return buildCreateResponse(fieldId, conflicts.size());
    }

    private void validateCropExists(long cropId) {
        if (fieldCropHistoryRepository.findCropIdIfExists(cropId).isEmpty()) {
            throw new IllegalArgumentException("Неизвестная культура (crop_id)");
        }
    }

    private ActionMessageResponse buildCreateResponse(long fieldId, int conflictsCount) {
        if (conflictsCount > 0) {
            return new ActionMessageResponse("Поле создано, пересечений найдено: " + conflictsCount, fieldId);
        }
        return new ActionMessageResponse("Поле создано", fieldId);
    }

    private String toGeometryGeoJson(NewFieldIntakeRequest request) {
        try {
            return objectMapper.writeValueAsString(request.geometry());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Некорректная геометрия поля", ex);
        }
    }
}
