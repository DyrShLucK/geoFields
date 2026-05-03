package com.geofields.controllers;

import com.geofields.repository.FieldRepository;
import com.geofields.repository.OrganizationRepository;
import com.geofields.service.AuthContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class NdviTileController {

    private final AuthContextService authContextService;
    private final FieldRepository fieldRepository;
    private final OrganizationRepository organizationRepository;

    public NdviTileController(
            AuthContextService authContextService,
            FieldRepository fieldRepository,
            OrganizationRepository organizationRepository) {
        this.authContextService = authContextService;
        this.fieldRepository = fieldRepository;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping("/get_ndvi_value")
    public ResponseEntity<Map<String, Object>> getNdviValue(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam String date) {
        // TODO: проверка в БД к Диме
        return ResponseEntity.ok(Map.of("value", 0.65));
    }

    @GetMapping("/get_ndvi_by_id")
    public ResponseEntity<Map<String, String>> getNdviById(
            @RequestParam String field_id,
            @RequestParam String date) {
        Long orgId = authContextService.getCurrentOrganizationId();
        if (orgId == null) {
            return ResponseEntity.status(401).build();
        }
        long fieldId;
        try {
            fieldId = Long.parseLong(field_id.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный field_id"));
        }
        if (!fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
            return ResponseEntity.notFound().build();
        }
        // TODO: проверка в БД иначе к Диме
        return ResponseEntity.ok(Map.of("url",
                "https://earthengine.googleapis.com/v1alpha/projects/stub/maps/FIELD_" + fieldId + "/tiles/{z}/{x}/{y}?token=stub"));
    }

    /** NDVI-плитка для всех полей организации текущего пользователя. */
    @GetMapping("/get_all_ndvi_tile")
    public ResponseEntity<Map<String, String>> getAllNdviTile(@RequestParam String date) {
        Long orgId = authContextService.getCurrentOrganizationId();
        if (orgId == null) {
            return ResponseEntity.status(401).build();
        }
        // TODO: проверка в БД иначе к Диме
        String orgName = organizationRepository.findNameById(orgId).orElse("");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("url",
                "https://earthengine.googleapis.com/v1alpha/projects/stub/maps/ORG_" + orgId + "_ALL/tiles/{z}/{x}/{y}?token=stub");
        body.put("organizationName", orgName);
        body.put("date", date);
        return ResponseEntity.ok(body);
    }
}
