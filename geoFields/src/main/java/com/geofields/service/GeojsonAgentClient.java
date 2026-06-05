package com.geofields.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofields.configuration.GeojsonAgentProperties;
import com.geofields.dto.imports.GeojsonAgentJobStatus;
import com.geofields.dto.imports.GeojsonAgentUploadResponse;
import com.geofields.dto.imports.ShapefileImportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * HTTP-клиент geojson-agent: загрузка shapefile, опрос статуса, скачивание GeoJSON.
 */
@Service
public class GeojsonAgentClient {

    private static final Logger log = LoggerFactory.getLogger(GeojsonAgentClient.class);

    /** Отдельный клиент только для multipart upload — uvicorn не принимает h2c от JDK HttpClient. */
    private static final HttpClient UPLOAD_HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final RestClient restClient;
    private final GeojsonAgentProperties properties;
    private final ObjectMapper objectMapper;

    public GeojsonAgentClient(
            RestClient geojsonAgentRestClient,
            GeojsonAgentProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = geojsonAgentRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ShapefileImportResponse convertArchive(MultipartFile archive) {
        String jobId = uploadArchive(archive);
        try {
            waitUntilCompleted(jobId);
            return downloadResult(jobId);
        } finally {
            cleanupQuietly(jobId);
        }
    }

    private String uploadArchive(MultipartFile archive) {
        try {
            String filename = StringUtils.hasText(archive.getOriginalFilename())
                    ? archive.getOriginalFilename()
                    : "upload.zip";
            byte[] bytes = archive.getBytes();
            String boundary = "GeofieldsBoundary" + UUID.randomUUID();
            byte[] body = GeojsonAgentMultipart.buildFilePart(
                    boundary, "file", filename, "application/zip", bytes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(properties.getBaseUrl()) + "/api/upload"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> httpResponse = UPLOAD_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int status = httpResponse.statusCode();
            String responseBody = httpResponse.body() == null ? "" : httpResponse.body();

            if (status >= 400) {
                throw new IllegalStateException(
                        "geojson-agent отклонил архив (HTTP " + status + "): " + trimBody(responseBody));
            }

            GeojsonAgentUploadResponse response =
                    objectMapper.readValue(responseBody, GeojsonAgentUploadResponse.class);
            if (response == null || !StringUtils.hasText(response.job_id())) {
                throw new IllegalStateException("geojson-agent не вернул идентификатор задачи");
            }
            log.info("geojson-agent: задача {} принята (файл '{}', {} байт)",
                    response.job_id(), filename, archive.getSize());
            return response.job_id();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Загрузка в geojson-agent прервана", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось отправить архив в geojson-agent: " + ex.getMessage(), ex);
        }
    }

    private void waitUntilCompleted(String jobId) {
        long deadline = System.currentTimeMillis() + properties.getMaxWaitMs();
        while (System.currentTimeMillis() < deadline) {
            GeojsonAgentJobStatus status = fetchStatus(jobId);
            if (status.isCompleted()) {
                log.info("geojson-agent: задача {} завершена", jobId);
                return;
            }
            if (status.isFailed()) {
                String detail = StringUtils.hasText(status.error()) ? status.error() : "неизвестная ошибка";
                throw new IllegalStateException("geojson-agent не смог обработать архив: " + detail);
            }
            sleep(properties.getPollIntervalMs());
        }
        throw new IllegalStateException(
                "Превышено время ожидания ответа geojson-agent (" + properties.getMaxWaitMs() / 1000 + " с)");
    }

    private GeojsonAgentJobStatus fetchStatus(String jobId) {
        try {
            GeojsonAgentJobStatus status = restClient.get()
                    .uri("/api/status/{jobId}", jobId)
                    .retrieve()
                    .body(GeojsonAgentJobStatus.class);
            if (status == null) {
                throw new IllegalStateException("geojson-agent вернул пустой статус для задачи " + jobId);
            }
            if (StringUtils.hasText(status.progress())) {
                log.debug("geojson-agent {}: {} — {}", jobId, status.status(), status.progress());
            }
            return status;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "geojson-agent: ошибка статуса (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("geojson-agent недоступен при опросе статуса: " + ex.getMessage(), ex);
        }
    }

    private ShapefileImportResponse downloadResult(String jobId) {
        try {
            ShapefileImportResponse response = restClient.get()
                    .uri("/api/download/{jobId}", jobId)
                    .retrieve()
                    .body(ShapefileImportResponse.class);
            if (response == null || response.fields() == null) {
                throw new IllegalStateException("geojson-agent вернул пустой результат");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "geojson-agent: не удалось скачать результат (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("geojson-agent недоступен при скачивании результата: " + ex.getMessage(), ex);
        }
    }

    private void cleanupQuietly(String jobId) {
        try {
            restClient.delete()
                    .uri("/api/cleanup/{jobId}", jobId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.debug("geojson-agent: cleanup {} пропущен: {}", jobId, ex.getMessage());
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание geojson-agent прервано", ex);
        }
    }

    private static String trimBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "…" : trimmed;
    }
}
