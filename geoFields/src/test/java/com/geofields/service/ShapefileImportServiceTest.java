package com.geofields.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofields.dto.imports.ShapefileImportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShapefileImportServiceTest {

    private final ShapefileImportService service = new ShapefileImportService(new ObjectMapper());

    @Test
    void importFromArchive_rejectsNullFile() {
        assertThatThrownBy(() -> service.importFromArchive(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("непустой ZIP");
    }

    @Test
    void importFromArchive_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]);

        assertThatThrownBy(() -> service.importFromArchive(empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("непустой ZIP");
    }

    @Test
    void importFromArchive_rejectsNonZipExtension() {
        MockMultipartFile txt = new MockMultipartFile("file", "fields.txt", "text/plain", "data".getBytes());

        assertThatThrownBy(() -> service.importFromArchive(txt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ZIP-архив");
    }

    @Test
    void importFromArchive_acceptsZipByContentType() {
        MockMultipartFile zip = new MockMultipartFile("file", "upload.bin", "application/zip", new byte[]{1, 2, 3});

        ShapefileImportResponse response = service.importFromArchive(zip);

        assertThat(response.fields()).isNotNull();
        assertThat(response.fields().features()).isNotEmpty();
        assertThat(response.fields().features().getFirst().properties().name()).isNotBlank();
        assertThat(response.crops()).isNotNull();
    }

    @Test
    void importFromArchive_readsStubGeoJsonFromClasspath() {
        MockMultipartFile zip = new MockMultipartFile("file", "import.zip", "application/zip", new byte[]{0x50, 0x4b});

        ShapefileImportResponse response = service.importFromArchive(zip);

        assertThat(response.fields().type()).isEqualTo("FeatureCollection");
        assertThat(response.fields().features().size()).isGreaterThan(1);
        assertThat(response.fields().features().getFirst().geometry().type()).isEqualTo("Polygon");
    }
}
