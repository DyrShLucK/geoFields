package com.geofields.service;

import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.agronomist.NewFieldIntakeRequest;
import com.geofields.security.GeoFieldsUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FieldAddService {

    private final FieldPersistenceService fieldPersistenceService;
    private final FieldCropHistoryService fieldCropHistoryService;
    private final GeoJsonSerializer geoJsonSerializer;

    public FieldAddService(
            FieldPersistenceService fieldPersistenceService,
            FieldCropHistoryService fieldCropHistoryService,
            GeoJsonSerializer geoJsonSerializer) {
        this.fieldPersistenceService = fieldPersistenceService;
        this.fieldCropHistoryService = fieldCropHistoryService;
        this.geoJsonSerializer = geoJsonSerializer;
    }

    @Transactional
    public ActionMessageResponse addField(NewFieldIntakeRequest request, GeoFieldsUserDetails user) {
        long organizationId = user.getOrganizationId();
        String geometryGeoJson = geoJsonSerializer.writeGeometry(request.geometry(), request.fieldName());
        FieldPersistenceService.CreatedField created = fieldPersistenceService.createWithIntersections(
                organizationId,
                request.fieldName(),
                geometryGeoJson);
        fieldCropHistoryService.insert(created.fieldId(), organizationId, request.history());
        return buildCreateResponse(created.fieldId(), created.conflictCount());
    }

    private ActionMessageResponse buildCreateResponse(long fieldId, int conflictsCount) {
        if (conflictsCount > 0) {
            return new ActionMessageResponse("Поле создано, пересечений найдено: " + conflictsCount, fieldId);
        }
        return new ActionMessageResponse("Поле создано", fieldId);
    }
}
