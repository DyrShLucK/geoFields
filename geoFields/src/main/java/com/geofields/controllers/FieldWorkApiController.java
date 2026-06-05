package com.geofields.controllers;

import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.fieldwork.FieldWorkCatalogResponse;
import com.geofields.dto.fieldwork.FieldWorkCreateRequest;
import com.geofields.dto.fieldwork.FieldWorkItemDetailResponse;
import com.geofields.dto.fieldwork.FieldWorkItemResponse;
import com.geofields.dto.fieldwork.FieldWorkUpdateRequest;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.service.FieldWorkService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
import java.util.NoSuchElementException;

/**
 * Агротехнические операции по полю (отдельно от /api/org/agronomist — севооборот).
 * Доступ: AGRONOMIST, ORG_ADMIN.
 */
@RestController
@RequestMapping("/api/field-work")
public class FieldWorkApiController {

    private static final Logger log = LoggerFactory.getLogger(FieldWorkApiController.class);

    private final FieldWorkService fieldWorkService;

    public FieldWorkApiController(FieldWorkService fieldWorkService) {
        this.fieldWorkService = fieldWorkService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<FieldWorkCatalogResponse> catalog() {
        return ResponseEntity.ok(fieldWorkService.loadCatalog());
    }

    @GetMapping("/fields/{fieldId}/items")
    public ResponseEntity<List<FieldWorkItemResponse>> listByField(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long fieldId) {
        try {
            return ResponseEntity.ok(fieldWorkService.listForField(fieldId, user.getOrganizationId()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/items/{operationId}")
    public ResponseEntity<FieldWorkItemDetailResponse> getItem(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long operationId) {
        return fieldWorkService.findDetail(operationId, user.getOrganizationId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/fields/{fieldId}/items")
    public ResponseEntity<ActionMessageResponse> createItem(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long fieldId,
            @Valid @RequestBody FieldWorkCreateRequest body) {
        try {
            ActionMessageResponse response = fieldWorkService.create(fieldId, body, user);
            log.info("userId={} fieldId={} operationId={} action=field_work_create success=true",
                    user.getUserId(), fieldId, response.id());
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            log.warn("userId={} fieldId={} action=field_work_create success=false reason={}",
                    user.getUserId(), fieldId, ex.getMessage());
            return errorResponse(ex);
        }
    }

    @PutMapping("/items/{operationId}")
    public ResponseEntity<ActionMessageResponse> updateItem(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long operationId,
            @Valid @RequestBody FieldWorkUpdateRequest body) {
        try {
            ActionMessageResponse response = fieldWorkService.update(operationId, body, user);
            log.info("userId={} operationId={} action=field_work_update success=true",
                    user.getUserId(), operationId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            log.warn("userId={} operationId={} action=field_work_update success=false reason={}",
                    user.getUserId(), operationId, ex.getMessage());
            return errorResponse(ex);
        }
    }

    @DeleteMapping("/items/{operationId}")
    public ResponseEntity<ActionMessageResponse> deleteItem(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @PathVariable long operationId) {
        try {
            ActionMessageResponse response = fieldWorkService.delete(operationId, user.getOrganizationId());
            log.info("userId={} operationId={} action=field_work_delete success=true",
                    user.getUserId(), operationId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            log.warn("userId={} operationId={} action=field_work_delete success=false reason={}",
                    user.getUserId(), operationId, ex.getMessage());
            return errorResponse(ex);
        }
    }

    private static ResponseEntity<ActionMessageResponse> errorResponse(Exception ex) {
        if (ex instanceof NoSuchElementException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ActionMessageResponse(ex.getMessage()));
        }
        return ResponseEntity.badRequest()
                .body(new ActionMessageResponse(ex.getMessage()));
    }
}
