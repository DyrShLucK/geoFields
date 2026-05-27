package com.geofields.service;

import com.geofields.configuration.NdviPythonProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Service
public class SlopePythonClient {

    private final RestClient restClient;
    private final NdviPythonProperties properties;

    public SlopePythonClient(RestClient ndviPythonRestClient, NdviPythonProperties properties) {
        this.restClient = ndviPythonRestClient;
        this.properties = properties;
    }

    public Map<String, Object> getTilesForField(long fieldId, String date) {
        Map<String, Object> body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/get_slope_tiles_for_field")
                        .queryParam("field_id", fieldId)
                        .queryParam("date", date)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return rewriteTileUrls(body);
    }

    public Map<String, Object> getTrend(List<Long> fieldIds, String startDate, String endDate) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/get_slope_trend")
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate);
        for (Long fieldId : fieldIds) {
            builder.queryParam("field_id", fieldId);
        }
        return restClient.get()
                .uri(builder.build().toUri())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public byte[] getTilePng(String fieldId, String date, String sceneId, int z, int x, int y) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/slope/tiles/{field_id}/{date}/{scene_id}/{z}/{x}/{y}.png")
                        .build(fieldId, date, sceneId, z, x, y))
                .accept(MediaType.IMAGE_PNG)
                .retrieve()
                .body(byte[].class);
    }

    public String upstreamDetail(RestClientResponseException ex) {
        return PythonClientSupport.upstreamDetail(ex);
    }

    private String rewriteTileUrl(String url) {
        return PythonClientSupport.rewriteProxyPath(url, "/slope/tiles/", properties.getPythonBaseUrl());
    }

    private Map<String, Object> rewriteTileUrls(Map<String, Object> body) {
        return PythonClientSupport.rewriteTileUrlInBody(body, "/slope/tiles/", properties.getPythonBaseUrl());
    }
}

