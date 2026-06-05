package com.geofields.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GeojsonAgentMultipartTest {

    @Test
    void buildFilePart_containsFieldNameFilenameAndBinaryPayload() throws Exception {
        byte[] body = GeojsonAgentMultipart.buildFilePart(
                "TestBoundary",
                "file",
                "archive.zip",
                "application/zip",
                new byte[]{'P', 'K', 3, 4});

        String text = new String(body, StandardCharsets.UTF_8);
        assertThat(text).startsWith("--TestBoundary\r\n");
        assertThat(text).contains("Content-Disposition: form-data; name=\"file\"; filename=\"archive.zip\"");
        assertThat(text).contains("Content-Type: application/zip");
        assertThat(text).endsWith("\r\n--TestBoundary--\r\n");
        assertThat(text).contains("PK\u0003\u0004");
    }
}
