package com.geofields.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка реального geojson-agent на localhost:6767 (если контейнер запущен).
 */
class GeojsonAgentUploadLiveTest {

    private static boolean agentAvailable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(2))
                    .build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder().uri(URI.create("http://localhost:6767/api/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200;
        } catch (Exception ex) {
            return false;
        }
    }

    @Test
    @EnabledIf("agentAvailable")
    void uploadUsesHttp11MultipartAcceptedByAgent() throws Exception {
        byte[] zip = new byte[] {0x50, 0x4b, 0x03, 0x04, 0x00, 0x00};
        String boundary = "GeofieldsBoundary" + UUID.randomUUID();
        byte[] body = GeojsonAgentMultipart.buildFilePart(
                boundary, "file", "probe.zip", "application/zip", zip);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:6767/api/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).contains("job_id");
    }

    @Test
    @EnabledIf("agentAvailable")
    void uploadWithHttp2IsRejectedOrMissingFile() throws Exception {
        byte[] zip = new byte[] {0x50, 0x4b, 0x03, 0x04};
        String boundary = "GeofieldsBoundary" + UUID.randomUUID();
        byte[] body = GeojsonAgentMultipart.buildFilePart(
                boundary, "file", "probe.zip", "application/zip", zip);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:6767/api/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // Документируем фактическое поведение: HTTP/2 h2c ломает разбор multipart у uvicorn.
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("file");
    }
}
