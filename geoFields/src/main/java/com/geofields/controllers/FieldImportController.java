package com.geofields.controllers;

import com.geofields.dto.imports.ShapefileImportCommitResponse;
import com.geofields.dto.imports.ShapefileImportResponse;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.service.FieldImportSaveService;
import com.geofields.service.ShapefileImportService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Импорт полей из shapefile.
 * <ol>
 *   <li>{@code POST /api/fields/import} — однократно отправляет архив во внешний сервис и
 *       возвращает GeoJSON для предпросмотра (без записи в БД).</li>
 *   <li>{@code POST /api/fields/import/commit} — сохраняет проверенные пользователем поля в БД.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/fields/import")
public class FieldImportController {

    private static final Logger log = LoggerFactory.getLogger(FieldImportController.class);
    private static final String FORBIDDEN_MESSAGE = "Операция доступна только пользователю организации";

    private final ShapefileImportService shapefileImportService;
    private final FieldImportSaveService fieldImportSaveService;

    public FieldImportController(
            ShapefileImportService shapefileImportService,
            FieldImportSaveService fieldImportSaveService) {
        this.shapefileImportService = shapefileImportService;
        this.fieldImportSaveService = fieldImportSaveService;
    }

    /** Шаг 1: распознавание полей из архива (предпросмотр, без сохранения). */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> importFields(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @RequestParam("file") MultipartFile file) {
        return executeForOrganization(user, () -> shapefileImportService.importFromArchive(file),
                "Ошибка импорта shapefile", "Не удалось обработать архив");
    }

    /** Шаг 2: сохранение проверенных полей в БД. */
    @PostMapping("/commit")
    public ResponseEntity<?> commitImport(
            @AuthenticationPrincipal GeoFieldsUserDetails user,
            @Valid @RequestBody ShapefileImportResponse payload) {
        return executeForOrganization(user, () -> fieldImportSaveService.save(payload, user),
                "Ошибка сохранения импортированных полей", "Не удалось сохранить поля");
    }

    private <T> ResponseEntity<?> executeForOrganization(
            GeoFieldsUserDetails user,
            Supplier<T> action,
            String errorLogMessage,
            String clientErrorMessage) {
        Optional<ResponseEntity<?>> forbidden = forbidWithoutOrganization(user);
        if (forbidden.isPresent()) {
            return forbidden.get();
        }
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error(errorLogMessage, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", clientErrorMessage));
        }
    }

    private static Optional<ResponseEntity<?>> forbidWithoutOrganization(GeoFieldsUserDetails user) {
        if (user == null || user.getOrganizationId() == null) {
            return Optional.of(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", FORBIDDEN_MESSAGE)));
        }
        return Optional.empty();
    }
}
