package com.geofields.service;

import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.fieldwork.FieldWorkCatalogResponse;
import com.geofields.dto.fieldwork.FieldWorkCodeItem;
import com.geofields.dto.fieldwork.FieldWorkCreateRequest;
import com.geofields.dto.fieldwork.FieldWorkItemDetailResponse;
import com.geofields.dto.fieldwork.FieldWorkItemResponse;
import com.geofields.dto.fieldwork.FieldWorkStatusHistoryItem;
import com.geofields.dto.fieldwork.FieldWorkUpdateRequest;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.FieldWorkRepository;
import com.geofields.repository.row.FieldWorkCodeRow;
import com.geofields.repository.row.FieldWorkItemRow;
import com.geofields.repository.row.FieldWorkStatusHistoryRow;
import com.geofields.security.GeoFieldsUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class FieldWorkService {

    private static final String DEFAULT_STATUS = "PLANNED";
    private static final String ERROR_OPERATION_NOT_FOUND = "Операция не найдена";
    private static final String ERROR_FIELD_NOT_FOUND = "Поле не найдено в организации";
    private static final String ERROR_STATUS_REQUIRED = "Укажите статус операции";
    private static final String ERROR_CATEGORY_REQUIRED = "Укажите категорию операции";

    private final FieldWorkRepository fieldWorkRepository;
    private final FieldRepository fieldRepository;

    public FieldWorkService(FieldWorkRepository fieldWorkRepository, FieldRepository fieldRepository) {
        this.fieldWorkRepository = fieldWorkRepository;
        this.fieldRepository = fieldRepository;
    }

    public FieldWorkCatalogResponse loadCatalog() {
        List<FieldWorkCodeItem> statuses = fieldWorkRepository.listStatuses().stream()
                .map(FieldWorkService::toCodeItem)
                .toList();
        List<FieldWorkCodeItem> categories = fieldWorkRepository.listCategories().stream()
                .map(FieldWorkService::toCodeItem)
                .toList();
        return new FieldWorkCatalogResponse(statuses, categories);
    }

    public List<FieldWorkItemResponse> listForField(long fieldId, long organizationId) {
        requireFieldInOrganization(fieldId, organizationId);
        return fieldWorkRepository.listByField(fieldId, organizationId).stream()
                .map(FieldWorkService::toItemResponse)
                .toList();
    }

    public Optional<FieldWorkItemDetailResponse> findDetail(long operationId, long organizationId) {
        return fieldWorkRepository.findById(operationId, organizationId)
                .map(item -> new FieldWorkItemDetailResponse(
                        toItemResponse(item),
                        fieldWorkRepository.listStatusHistory(operationId).stream()
                                .map(FieldWorkService::toHistoryItem)
                                .toList()));
    }

    @Transactional
    public ActionMessageResponse create(long fieldId, FieldWorkCreateRequest request, GeoFieldsUserDetails user) {
        long orgId = user.getOrganizationId();
        requireFieldInOrganization(fieldId, orgId);
        String status = resolveCreateStatus(request.status());
        String categoryCode = normalizeCategory(request.category());

        long operationId = fieldWorkRepository.insertOperation(
                fieldId,
                orgId,
                user.getUserId(),
                normalizeName(request.name()),
                categoryCode,
                status,
                request.operationAt());
        fieldWorkRepository.insertStatusHistory(
                operationId,
                status,
                user.getUserId(),
                trimNote(request.statusNote()));
        return new ActionMessageResponse("Операция создана", operationId);
    }

    @Transactional
    public ActionMessageResponse update(long operationId, FieldWorkUpdateRequest request, GeoFieldsUserDetails user) {
        long orgId = user.getOrganizationId();
        FieldWorkItemRow existing = requireOperation(operationId, orgId);
        String categoryCode = normalizeCategory(request.category());

        String newStatus = resolveUpdateStatus(request.status(), existing.status());

        int updated = fieldWorkRepository.updateOperation(
                operationId,
                orgId,
                normalizeName(request.name()),
                categoryCode,
                newStatus,
                request.operationAt());
        if (updated == 0) {
            throw new NoSuchElementException(ERROR_OPERATION_NOT_FOUND);
        }

        if (!newStatus.equals(existing.status())) {
            fieldWorkRepository.insertStatusHistory(
                    operationId,
                    newStatus,
                    user.getUserId(),
                    trimNote(request.statusNote()));
        }

        return new ActionMessageResponse("Операция обновлена", operationId);
    }

    @Transactional
    public ActionMessageResponse delete(long operationId, long organizationId) {
        if (!fieldWorkRepository.operationBelongsToOrganization(operationId, organizationId)) {
            throw new NoSuchElementException(ERROR_OPERATION_NOT_FOUND);
        }
        int n = fieldWorkRepository.deleteOperation(operationId, organizationId);
        if (n == 0) {
            throw new NoSuchElementException(ERROR_OPERATION_NOT_FOUND);
        }
        return new ActionMessageResponse("Операция удалена", operationId);
    }

    private void requireFieldInOrganization(long fieldId, long organizationId) {
        if (!fieldRepository.fieldBelongsToOrganization(fieldId, organizationId)) {
            throw new NoSuchElementException(ERROR_FIELD_NOT_FOUND);
        }
    }

    private FieldWorkItemRow requireOperation(long operationId, long organizationId) {
        return fieldWorkRepository.findById(operationId, organizationId)
                .orElseThrow(() -> new NoSuchElementException(ERROR_OPERATION_NOT_FOUND));
    }

    private String resolveCreateStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_STATUS;
        }
        return normalizeStatus(raw);
    }

    private String resolveUpdateStatus(String raw, String existingStatus) {
        if (raw == null || raw.isBlank()) {
            return existingStatus;
        }
        return normalizeStatus(raw);
    }

    private String normalizeStatus(String raw) {
        String code = normalizeCode(raw, ERROR_STATUS_REQUIRED);
        if (!fieldWorkRepository.statusExists(code)) {
            throw new IllegalArgumentException("Неизвестный статус: " + code);
        }
        return code;
    }

    private String normalizeCategory(String raw) {
        String code = normalizeCode(raw, ERROR_CATEGORY_REQUIRED);
        if (!fieldWorkRepository.categoryExists(code)) {
            throw new IllegalArgumentException("Неизвестная категория: " + code);
        }
        return code;
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Укажите название операции");
        }
        return raw.trim();
    }

    private static String normalizeCode(String raw, String requiredMessage) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(requiredMessage);
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private static FieldWorkCodeItem toCodeItem(FieldWorkCodeRow row) {
        return new FieldWorkCodeItem(row.code(), row.titleRu());
    }

    private static FieldWorkItemResponse toItemResponse(FieldWorkItemRow row) {
        return new FieldWorkItemResponse(
                row.id(),
                row.fieldId(),
                row.name(),
                row.category(),
                row.categoryTitleRu(),
                row.status(),
                row.statusTitleRu(),
                row.operationAt(),
                row.userId(),
                row.createdAt(),
                row.updatedAt());
    }

    private static FieldWorkStatusHistoryItem toHistoryItem(FieldWorkStatusHistoryRow row) {
        String displayName = formatUserName(row.userLastName(), row.userFirstName());
        return new FieldWorkStatusHistoryItem(
                row.id(),
                row.status(),
                row.statusTitleRu(),
                row.changedAt(),
                row.userId(),
                displayName,
                row.note());
    }

    private static String formatUserName(String lastName, String firstName) {
        String ln = lastName != null ? lastName.trim() : "";
        String fn = firstName != null ? firstName.trim() : "";
        if (ln.isEmpty() && fn.isEmpty()) {
            return null;
        }
        if (ln.isEmpty()) {
            return fn;
        }
        if (fn.isEmpty()) {
            return ln;
        }
        return ln + " " + fn;
    }
}
