package com.geofields.service;

import com.geofields.dto.imports.ShapefileImportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShapefileImportServiceTest {

    @Mock
    private GeojsonAgentClient geojsonAgentClient;

    @InjectMocks
    private ShapefileImportService service;

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
    void importFromArchive_delegatesToGeojsonAgent() {
        MockMultipartFile zip = new MockMultipartFile("file", "import.zip", "application/zip", new byte[]{0x50, 0x4b});
        ShapefileImportResponse expected = ImportTestFixtures.sampleImportResponse();
        when(geojsonAgentClient.convertArchive(any())).thenReturn(expected);

        ShapefileImportResponse response = service.importFromArchive(zip);

        assertThat(response).isSameAs(expected);
        verify(geojsonAgentClient).convertArchive(zip);
    }
}
