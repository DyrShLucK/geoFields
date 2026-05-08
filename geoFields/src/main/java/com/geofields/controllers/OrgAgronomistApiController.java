package com.geofields.controllers;

import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.agronomist.AgronomistSummaryResponse;
import com.geofields.dto.agronomist.CropOptionItem;
import com.geofields.dto.agronomist.FieldCropHistoryItem;
import com.geofields.dto.agronomist.FieldCropUpsertRequest;
import com.geofields.dto.agronomist.FieldOptionItem;
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
        long orgId = user.getOrganizationId();
        if (!fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
            return ResponseEntity.notFound().build();
        }
        if (fieldCropHistoryRepository.findCropIdIfExists(body.cropId()).isEmpty()) {
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
        return ResponseEntity.ok(new ActionMessageResponse("Запись добавлена", id));
    }

    @PostMapping("/fields/intake")
    public ResponseEntity<ActionMessageResponse> intakeNewField(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @Valid @RequestBody NewFieldIntakeRequest body) {
        long orgId = user.getOrganizationId();
        fieldAddService.AddField(body, user);
        return ResponseEntity.ok(new ActionMessageResponse(
                "Контур нового поля и первая запись истории приняты (черновой endpoint), orgId=" + orgId));
    }

    @PutMapping("/history/{historyId}")
    public ResponseEntity<ActionMessageResponse> updateHistory(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long historyId,
            @Valid @RequestBody FieldCropUpsertRequest body) {
        long orgId = user.getOrganizationId();
        if (!fieldCropHistoryRepository.historyBelongsToOrganization(historyId, orgId)) {
            return ResponseEntity.notFound().build();
        }
        if (fieldCropHistoryRepository.findCropIdIfExists(body.cropId()).isEmpty()) {
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
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ActionMessageResponse("Запись обновлена", historyId));
    }

    @DeleteMapping("/history/{historyId}")
    public ResponseEntity<ActionMessageResponse> deleteHistory(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long historyId) {
        long orgId = user.getOrganizationId();
        int n = fieldCropHistoryRepository.deleteHistory(historyId, orgId);
        if (n == 0) {
            return ResponseEntity.notFound().build();
        }
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
