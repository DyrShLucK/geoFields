package com.geofields.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofields.configuration.GeojsonAgentProperties;
import com.geofields.dto.imports.ShapefileImportResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeojsonAgentClientTest {

    private HttpServer uploadServer;
    private String uploadBaseUrl;
    private MockRestServiceServer restServer;
    private GeojsonAgentClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        uploadServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = uploadServer.getAddress().getPort();
        uploadBaseUrl = "http://localhost:" + port;

        RestClient.Builder restBuilder = RestClient.builder().baseUrl("http://geojson-agent.test");
        restServer = MockRestServiceServer.bindTo(restBuilder).build();

        GeojsonAgentProperties properties = new GeojsonAgentProperties();
        properties.setBaseUrl(uploadBaseUrl);
        properties.setPollIntervalMs(1);
        properties.setMaxWaitMs(5_000);

        client = new GeojsonAgentClient(
                restBuilder.build(),
                properties,
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (uploadServer != null) {
            uploadServer.stop(0);
        }
        restServer.verify();
    }

    @Test
    void convertArchive_pollsUntilCompletedAndDownloadsResult() throws Exception {
        ShapefileImportResponse payload = ImportTestFixtures.sampleImportResponse();
        String resultJson = objectMapper.writeValueAsString(payload);

        uploadServer.createContext("/api/upload", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String text = new String(body, StandardCharsets.UTF_8);
            assertThat(text).contains("name=\"file\"");
            assertThat(text).contains("filename=\"fields.zip\"");
            byte[] response = "{\"job_id\":\"job-1\",\"status\":\"pending\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(202, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        uploadServer.start();

        restServer.expect(requestTo("http://geojson-agent.test/api/status/job-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"job_id\":\"job-1\",\"status\":\"processing\",\"progress\":\"маппинг\"}",
                        MediaType.APPLICATION_JSON));

        restServer.expect(requestTo("http://geojson-agent.test/api/status/job-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"job_id\":\"job-1\",\"status\":\"completed\",\"result_url\":\"/api/download/job-1\"}",
                        MediaType.APPLICATION_JSON));

        restServer.expect(requestTo("http://geojson-agent.test/api/download/job-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(resultJson, MediaType.APPLICATION_JSON));

        restServer.expect(requestTo("http://geojson-agent.test/api/cleanup/job-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        MockMultipartFile zip = new MockMultipartFile("file", "fields.zip", "application/zip", new byte[]{1, 2, 3});
        ShapefileImportResponse response = client.convertArchive(zip);

        assertThat(response.fields().features()).hasSize(1);
        assertThat(response.fields().features().getFirst().properties().name()).contains("Поле");
    }

    @Test
    void convertArchive_failsWhenJobFailed() {
        uploadServer.createContext("/api/upload", exchange -> {
            byte[] response = "{\"job_id\":\"job-2\",\"status\":\"pending\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(202, response.length);
                exchange.getResponseBody().write(response);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        uploadServer.start();

        restServer.expect(requestTo("http://geojson-agent.test/api/status/job-2"))
                .andRespond(withSuccess(
                        "{\"job_id\":\"job-2\",\"status\":\"failed\",\"error\":\"нет .shp\"}",
                        MediaType.APPLICATION_JSON));
        restServer.expect(requestTo("http://geojson-agent.test/api/cleanup/job-2"))
                .andRespond(withSuccess());

        MockMultipartFile zip = new MockMultipartFile("file", "bad.zip", "application/zip", new byte[]{9});

        assertThatThrownBy(() -> client.convertArchive(zip))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("нет .shp");
    }

    @Test
    void convertArchive_failsWhenUploadRejected() {
        uploadServer.createContext("/api/upload", exchange -> {
            byte[] response = "{\"detail\":\"bad\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(422, response.length);
                exchange.getResponseBody().write(response);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        uploadServer.start();

        MockMultipartFile zip = new MockMultipartFile("file", "bad.zip", "application/zip", new byte[]{9});

        assertThatThrownBy(() -> client.convertArchive(zip))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("отклонил архив");
    }
}
