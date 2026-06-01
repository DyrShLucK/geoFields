package com.geofields.service;

import com.geofields.configuration.NdviPythonProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class ElevationPythonClient {

    private static final Map<String, String> PREPARE_PATH_FIELDS = Map.of(
            "tile_url", "/tiles/elevation/",
            "slope_tile_url", "/tiles/slope/");

    private final RestClient restClient;
    private final NdviPythonProperties properties;

    public ElevationPythonClient(RestClient ndviPythonRestClient, NdviPythonProperties properties) {
        this.restClient = ndviPythonRestClient;
        this.properties = properties;
    }

    public Map<String, Object> prepareElevation(long fieldId) {
        Map<String, Object> body = restClient.get()
                .uri("/prepare_elevation/{field_id}", fieldId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return rewritePrepareResponse(body);
    }

    public byte[] getElevationTilePng(String fieldId, int z, int x, int y) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tiles/elevation/{field_id}/{z}/{x}/{y}.png")
                        .build(fieldId, z, x, y))
                .accept(MediaType.IMAGE_PNG)
                .retrieve()
                .body(byte[].class);
    }

    public byte[] getTerrainSlopeTilePng(String fieldId, int z, int x, int y) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tiles/slope/{field_id}/{z}/{x}/{y}.png")
                        .build(fieldId, z, x, y))
                .accept(MediaType.IMAGE_PNG)
                .retrieve()
                .body(byte[].class);
    }

    public String upstreamDetail(RestClientResponseException ex) {
        return PythonClientSupport.upstreamDetail(ex);
    }

    private Map<String, Object> rewritePrepareResponse(Map<String, Object> body) {
        return PythonClientSupport.rewritePathsInBody(body, properties.getPythonBaseUrl(), PREPARE_PATH_FIELDS);
    }
}
