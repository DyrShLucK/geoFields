package com.geofields.controllers;

import com.geofields.dto.imports.ShapefileImportCommitResponse;
import com.geofields.dto.imports.ShapefileImportResponse;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;
import com.geofields.service.FieldImportSaveService;
import com.geofields.service.ImportTestFixtures;
import com.geofields.service.ShapefileImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldImportControllerTest {

    @Mock
    private ShapefileImportService shapefileImportService;

    @Mock
    private FieldImportSaveService fieldImportSaveService;

    @InjectMocks
    private FieldImportController controller;

    @Test
    void importFields_returnsForbiddenWithoutOrganization() {
        ResponseEntity<?> response = controller.importFields(userWithoutOrg(), zipFile());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Операция доступна только пользователю организации"));
    }

    @Test
    void importFields_returnsBadRequestOnValidationError() {
        MockMultipartFile file = zipFile();
        when(shapefileImportService.importFromArchive(file))
                .thenThrow(new IllegalArgumentException("Ожидается ZIP-архив (.zip) с shapefile"));

        ResponseEntity<?> response = controller.importFields(ImportTestFixtures.agronomistUser(), file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Ожидается ZIP-архив (.zip) с shapefile"));
    }

    @Test
    void importFields_returnsPreviewPayload() {
        MockMultipartFile file = zipFile();
        ShapefileImportResponse preview = ImportTestFixtures.sampleImportResponse();
        when(shapefileImportService.importFromArchive(file)).thenReturn(preview);

        ResponseEntity<?> response = controller.importFields(ImportTestFixtures.agronomistUser(), file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(preview);
    }

    @Test
    void commitImport_returnsForbiddenWithoutOrganization() {
        ResponseEntity<?> response = controller.commitImport(null, ImportTestFixtures.sampleImportResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void commitImport_returnsCommitResult() {
        GeoFieldsUserDetails user = ImportTestFixtures.agronomistUser();
        ShapefileImportResponse payload = ImportTestFixtures.sampleImportResponse();
        ShapefileImportCommitResponse commit = new ShapefileImportCommitResponse(1, 1, 0, "Импортировано полей: 1");
        when(fieldImportSaveService.save(payload, user)).thenReturn(commit);

        ResponseEntity<?> response = controller.commitImport(user, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(commit);
    }

    @Test
    void commitImport_returnsBadRequestWhenSaveFailsValidation() {
        GeoFieldsUserDetails user = ImportTestFixtures.agronomistUser();
        ShapefileImportResponse payload = ImportTestFixtures.sampleImportResponse();
        when(fieldImportSaveService.save(payload, user))
                .thenThrow(new IllegalArgumentException("Нет полей для сохранения"));

        ResponseEntity<?> response = controller.commitImport(user, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Нет полей для сохранения"));
    }

    private static MockMultipartFile zipFile() {
        return new MockMultipartFile("file", "fields.zip", "application/zip", new byte[]{1, 2, 3});
    }

    private static GeoFieldsUserDetails userWithoutOrg() {
        return new GeoFieldsUserDetails(
                1L,
                null,
                "guest",
                "hash",
                UserRole.USER,
                true,
                "Guest",
                "User",
                null);
    }
}
