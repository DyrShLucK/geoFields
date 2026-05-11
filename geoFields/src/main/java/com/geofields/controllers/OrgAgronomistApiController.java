package com.geofields.controllers;

import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.agronomist.AgronomistSummaryResponse;
import com.geofields.dto.agronomist.CropOptionItem;
import com.geofields.dto.agronomist.FieldCropHistoryItem;
import com.geofields.dto.agronomist.FieldCropUpsertRequest;
import com.geofields.dto.agronomist.FieldOptionItem;
import com.geofields.dto.agronomist.FieldStatusUpdateRequest;
import com.geofields.dto.agronomist.NewFieldIntakeRequest;
import com.geofields.dto.orgmanager.CsrfInfo;
import com.geofields.repository.FieldCropHistoryRepository;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.OrganizationRepository;
import com.geofields.repository.row.CropOptionRow;
import com.geofields.repository.row.FieldCropHistoryRow;
import com.geofields.repository.row.FieldOptionRow;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.support.web.CsrfTokenReader;
import com.geofields.service.FieldAddService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/org/agronomist")
public class OrgAgronomistApiController {
    private static final Logger log = LoggerFactory.getLogger(OrgAgronomistApiController.class);

    private final FieldRepository fieldRepository;
    private final FieldCropHistoryRepository fieldCropHistoryRepository;
    private final OrganizationRepository organizationRepository;
    private final FieldAddService fieldAddService;

    public OrgAgronomistApiController(
            FieldRepository fieldRepository,
            FieldCropHistoryRepository fieldCropHistoryRepository,
            OrganizationRepository organizationRepository, FieldAddService fieldAddService) {
        this.fieldRepository = fieldRepository;
        this.fieldCropHistoryRepository = fieldCropHistoryRepository;
        this.organizationRepository = organizationRepository;
        this.fieldAddService = fieldAddService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AgronomistSummaryResponse> summary(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            HttpServletRequest request) {
        long orgId = user.getOrganizationId();
        List<FieldOptionItem> fields = fieldCropHistoryRepository.listFieldsForOrganization(orgId).stream()
                .map(OrgAgronomistApiController::toFieldItem)
                .toList();
        List<FieldOptionItem> obsoleteFields = fieldCropHistoryRepository.listObsoleteFieldsForOrganization(orgId).stream()
                .map(OrgAgronomistApiController::toFieldItem)
                .toList();
        List<CropOptionItem> crops = fieldCropHistoryRepository.listAllCrops().stream()
                .map(OrgAgronomistApiController::toCropItem)
                .toList();
        CsrfInfo csrf = CsrfTokenReader.read(request);
        String orgName = organizationRepository.findNameById(orgId).orElse("");
        return ResponseEntity.ok(new AgronomistSummaryResponse(
                orgId,
                orgName,
                user.getUsername(),
                user.getFullName(),
                fields,
                obsoleteFields,
                crops,
                csrf));
    }

    @GetMapping("/fields/{fieldId}/history")
    public ResponseEntity<List<FieldCropHistoryItem>> history(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long fieldId) {
        long orgId = user.getOrganizationId();
        if (!fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
            return ResponseEntity.notFound().build();
        }
        List<FieldCropHistoryItem> body = fieldCropHistoryRepository
                .findHistoryByFieldAndOrganization(fieldId, orgId).stream()
                .map(OrgAgronomistApiController::toHistoryItem)
                .toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/fields/{fieldId}/history")
    public ResponseEntity<ActionMessageResponse> createHistory(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long fieldId,
            @Valid @RequestBody FieldCropUpsertRequest body) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        if (!fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
            log.warn("userId={} orgId={} action=create_history fieldId={} success=false reason=not_found_or_foreign",
                    actorUserId, orgId, fieldId);
            return ResponseEntity.notFound().build();
        }
        if (fieldCropHistoryRepository.findCropIdIfExists(body.cropId()).isEmpty()) {
            log.warn("userId={} orgId={} action=create_history fieldId={} cropId={} success=false reason=unknown_crop",
                    actorUserId, orgId, fieldId, body.cropId());
            return ResponseEntity.badRequest().body(new ActionMessageResponse("Неизвестная культура (crop_id)"));
        }
        long id = fieldCropHistoryRepository.insertHistory(
                fieldId,
                orgId,
                body.cropId(),
                body.sowingDate(),
                body.harvestDate(),
                body.sownAreaHa(),
                body.harvestAreaHa(),
                body.actualYield(),
                body.totalYield(),
                body.plannedYield(),
                body.forecastedYield(),
                body.sourceData(),
                body.sowingDetails(),
                body.cropYear());
        log.info("userId={} orgId={} action=create_history fieldId={} historyId={} success=true",
                actorUserId, orgId, fieldId, id);
        return ResponseEntity.ok(new ActionMessageResponse("Запись добавлена", id));
    }

    @PostMapping("/fields/intake")
    public ResponseEntity<ActionMessageResponse> intakeNewField(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @Valid @RequestBody NewFieldIntakeRequest body) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        try {
            ActionMessageResponse response = fieldAddService.addField(body, user);
            log.info("userId={} orgId={} action=create_field fieldId={} success=true",
                    actorUserId, orgId, response.id());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            log.warn("userId={} orgId={} action=create_field success=false reason={}",
                    actorUserId, orgId, ex.getMessage());
            return ResponseEntity.badRequest().body(new ActionMessageResponse(ex.getMessage()));
        }
    }

    @PutMapping("/history/{historyId}")
    public ResponseEntity<ActionMessageResponse> updateHistory(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long historyId,
            @Valid @RequestBody FieldCropUpsertRequest body) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        if (!fieldCropHistoryRepository.historyBelongsToOrganization(historyId, orgId)) {
            log.warn("userId={} orgId={} action=update_history historyId={} success=false reason=not_found_or_foreign",
                    actorUserId, orgId, historyId);
            return ResponseEntity.notFound().build();
        }
        if (fieldCropHistoryRepository.findCropIdIfExists(body.cropId()).isEmpty()) {
            log.warn("userId={} orgId={} action=update_history historyId={} cropId={} success=false reason=unknown_crop",
                    actorUserId, orgId, historyId, body.cropId());
            return ResponseEntity.badRequest().body(new ActionMessageResponse("Неизвестная культура (crop_id)"));
        }
        int n = fieldCropHistoryRepository.updateHistory(
                historyId,
                orgId,
                body.cropId(),
                body.sowingDate(),
                body.harvestDate(),
                body.sownAreaHa(),
                body.harvestAreaHa(),
                body.actualYield(),
                body.totalYield(),
                body.plannedYield(),
                body.forecastedYield(),
                body.sourceData(),
                body.sowingDetails(),
                body.cropYear());
        if (n == 0) {
            log.warn("userId={} orgId={} action=update_history historyId={} success=false reason=no_rows_updated",
                    actorUserId, orgId, historyId);
            return ResponseEntity.notFound().build();
        }
        log.info("userId={} orgId={} action=update_history historyId={} success=true",
                actorUserId, orgId, historyId);
        return ResponseEntity.ok(new ActionMessageResponse("Запись обновлена", historyId));
    }

    @PutMapping("/fields/{fieldId}/status")
    public ResponseEntity<ActionMessageResponse> updateFieldStatus(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long fieldId,
            @Valid @RequestBody FieldStatusUpdateRequest body) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        if (!fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
            log.warn("userId={} orgId={} action=update_field_status fieldId={} success=false reason=not_found_or_foreign",
                    actorUserId, orgId, fieldId);
            return ResponseEntity.notFound().build();
        }

        boolean active;
        String normalized = body.status().trim().toUpperCase();
        if ("ACTIVE".equals(normalized)) {
            active = true;
        } else if ("OBSOLETE".equals(normalized)) {
            active = false;
        } else {
            log.warn("userId={} orgId={} action=update_field_status fieldId={} status={} success=false reason=invalid_status",
                    actorUserId, orgId, fieldId, body.status());
            return ResponseEntity.badRequest()
                    .body(new ActionMessageResponse("Допустимые статусы: ACTIVE, OBSOLETE"));
        }

        int updated = fieldRepository.updateFieldActiveStatus(fieldId, orgId, active);
        if (updated == 0) {
            log.warn("userId={} orgId={} action=update_field_status fieldId={} status={} success=false reason=no_rows_updated",
                    actorUserId, orgId, fieldId, normalized);
            return ResponseEntity.notFound().build();
        }
        log.info("userId={} orgId={} action=update_field_status fieldId={} status={} success=true",
                actorUserId, orgId, fieldId, normalized);
        return ResponseEntity.ok(new ActionMessageResponse(
                active ? "Поле снова активно" : "Поле помечено как устаревшее",
                fieldId));
    }

    @DeleteMapping("/history/{historyId}")
    public ResponseEntity<ActionMessageResponse> deleteHistory(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long historyId) {
        long actorUserId = user.getUserId();
        long orgId = user.getOrganizationId();
        if (!fieldCropHistoryRepository.historyBelongsToOrganization(historyId, orgId)) {
            log.warn("userId={} orgId={} action=delete_history historyId={} success=false reason=not_found_or_foreign",
                    actorUserId, orgId, historyId);
            return ResponseEntity.notFound().build();
        }
        Long fieldId = fieldCropHistoryRepository.findFieldIdByHistoryId(historyId, orgId).orElse(null);
        if (fieldId == null) {
            log.warn("userId={} orgId={} action=delete_history historyId={} success=false reason=field_not_found",
                    actorUserId, orgId, historyId);
            return ResponseEntity.notFound().build();
        }
        if (fieldCropHistoryRepository.countHistoryByFieldAndOrganization(fieldId, orgId) <= 1) {
            log.warn("userId={} orgId={} action=delete_history historyId={} fieldId={} success=false reason=last_history_row",
                    actorUserId, orgId, historyId, fieldId);
            return ResponseEntity.badRequest()
                    .body(new ActionMessageResponse("Нельзя удалить последнюю запись истории поля"));
        }
        int n = fieldCropHistoryRepository.deleteHistory(historyId, orgId);
        if (n == 0) {
            log.warn("userId={} orgId={} action=delete_history historyId={} fieldId={} success=false reason=no_rows_deleted",
                    actorUserId, orgId, historyId, fieldId);
            return ResponseEntity.notFound().build();
        }
        log.info("userId={} orgId={} action=delete_history historyId={} fieldId={} success=true",
                actorUserId, orgId, historyId, fieldId);
        return ResponseEntity.ok(new ActionMessageResponse("Запись удалена"));
    }

    private static FieldOptionItem toFieldItem(FieldOptionRow r) {
        return new FieldOptionItem(r.fieldId(), r.fieldName());
    }

    private static CropOptionItem toCropItem(CropOptionRow r) {
        return new CropOptionItem(r.cropId(), r.cropName());
    }

    private static FieldCropHistoryItem toHistoryItem(FieldCropHistoryRow r) {
        return new FieldCropHistoryItem(
                r.historyId(),
                r.cropId(),
                r.cropName(),
                r.sowingDate(),
                r.harvestDate(),
                r.sownAreaHa(),
                r.harvestAreaHa(),
                r.actualYield(),
                r.totalYield(),
                r.plannedYield(),
                r.forecastedYield(),
                r.sourceData(),
                r.sowingDetails(),
                r.cropYear());
    }
}
