package com.geofields.service;

import com.geofields.repository.FieldRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Service
public class RequestValidationService {

    private final AuthContextService authContextService;
    private final FieldRepository fieldRepository;

    public RequestValidationService(AuthContextService authContextService, FieldRepository fieldRepository) {
        this.authContextService = authContextService;
        this.fieldRepository = fieldRepository;
    }

    public ValidationResult<Long> requireOrganizationId() {
        Long orgId = authContextService.getCurrentOrganizationId();
        if (orgId == null) {
            return ValidationResult.error(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return ValidationResult.ok(orgId);
    }

    public ValidationResult<Long> parseFieldId(String fieldIdRaw) {
        if (fieldIdRaw == null || fieldIdRaw.isBlank()) {
            return ValidationResult.error(badRequest("Некорректный field_id"));
        }
        try {
            return ValidationResult.ok(Long.parseLong(fieldIdRaw.trim()));
        } catch (NumberFormatException e) {
            return ValidationResult.error(badRequest("Некорректный field_id"));
        }
    }

    public ValidationResult<Void> ensureFieldBelongsToOrganization(long fieldId, long orgId, boolean withBody) {
        if (fieldRepository.fieldBelongsToOrganization(fieldId, orgId)) {
            return ValidationResult.ok(null);
        }
        if (withBody) {
            return ValidationResult.error(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Поле " + fieldId + " не принадлежит организации")));
        }
        return ValidationResult.error(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    public ValidationResult<Long> requireOwnedFieldId(String fieldIdRaw, long orgId, boolean withBody) {
        ValidationResult<Long> parsed = parseFieldId(fieldIdRaw);
        if (parsed.isError()) {
            return ValidationResult.error(parsed.errorResponse());
        }
        ValidationResult<Void> fieldAccess = ensureFieldBelongsToOrganization(parsed.value(), orgId, withBody);
        if (fieldAccess.isError()) {
            return ValidationResult.error(fieldAccess.errorResponse());
        }
        return ValidationResult.ok(parsed.value());
    }

    public ValidationResult<List<Long>> ensureFieldsBelongToOrganization(List<Long> fieldIds, long orgId, boolean withBody) {
        for (Long fieldId : fieldIds) {
            ValidationResult<Void> fieldAccess = ensureFieldBelongsToOrganization(fieldId, orgId, withBody);
            if (fieldAccess.isError()) {
                return ValidationResult.error(fieldAccess.errorResponse());
            }
        }
        return ValidationResult.ok(fieldIds);
    }

    public ValidationResult<List<Long>> parseFieldIds(List<String> rawFieldIds) {
        try {
            List<Long> fieldIds = FieldIdRequestParser.parseFieldIds(rawFieldIds);
            if (fieldIds.isEmpty()) {
                return ValidationResult.error(badRequest("Укажите хотя бы одно поле"));
            }
            return ValidationResult.ok(fieldIds);
        } catch (IllegalArgumentException e) {
            return ValidationResult.error(badRequest(e.getMessage()));
        }
    }

    public ValidationResult<LocalDate> parseDate(String dateRaw) {
        if (dateRaw == null || dateRaw.isBlank()) {
            return ValidationResult.error(badRequest("Некорректная дата, ожидается YYYY-MM-DD"));
        }
        try {
            return ValidationResult.ok(LocalDate.parse(dateRaw.trim()));
        } catch (DateTimeParseException e) {
            return ValidationResult.error(badRequest("Некорректная дата, ожидается YYYY-MM-DD"));
        }
    }

    public ValidationResult<DateRange> parseDateRange(String startDateRaw, String endDateRaw) {
        ValidationResult<LocalDate> start = parseDate(startDateRaw);
        if (start.isError()) {
            return ValidationResult.error(start.errorResponse());
        }
        ValidationResult<LocalDate> end = parseDate(endDateRaw);
        if (end.isError()) {
            return ValidationResult.error(end.errorResponse());
        }
        return ValidationResult.ok(new DateRange(start.value(), end.value()));
    }

    public record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    public record ValidationResult<T>(T value, ResponseEntity<?> errorResponse) {
        public static <T> ValidationResult<T> ok(T value) {
            return new ValidationResult<>(value, null);
        }

        public static <T> ValidationResult<T> error(ResponseEntity<?> errorResponse) {
            return new ValidationResult<>(null, errorResponse);
        }

        public boolean isError() {
            return errorResponse != null;
        }
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}

