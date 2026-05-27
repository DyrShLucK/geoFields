package com.geofields.service;

import com.geofields.configuration.NdviPythonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;

@Service
public class NdviPythonClient {

    private static final Logger log = LoggerFactory.getLogger(NdviPythonClient.class);

    private final RestClient restClient;
    private final NdviPythonProperties properties;

    public NdviPythonClient(RestClient ndviPythonRestClient, NdviPythonProperties properties) {
        this.restClient = ndviPythonRestClient;
        this.properties = properties;
    }

    public Map<String, Object> getTilesForField(long fieldId, String date) {
        Map<String, Object> body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/get_ndvi_tiles_for_field")
                        .queryParam("field_id", fieldId)
                        .queryParam("date", date)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return rewriteTileUrls(body);
    }

    public Map<String, Object> getTrend(List<Long> fieldIds, String startDate, String endDate) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/get_ndvi_trend")
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

    /**
     * Прогрев тренда: для каждого поля запрашивает NDVI по первому дню каждого месяца.
     * Это не меняет Python API, но инициирует расчёты до запроса графика.
     */
    public void warmupTrendAnalytics(List<Long> fieldIds, String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        LocalDate cursor = start.withDayOfMonth(1);
        LocalDate endMonth = end.withDayOfMonth(1);
        while (!cursor.isAfter(endMonth)) {
            String monthDate = cursor.toString();
            for (Long fieldId : fieldIds) {
                try {
                    getTilesForField(fieldId, monthDate);
                } catch (RestClientResponseException ex) {
                    log.debug("NDVI warmup upstream {} for field={} date={}: {}",
                            ex.getStatusCode(), fieldId, monthDate, upstreamDetail(ex));
                } catch (RuntimeException ex) {
                    log.debug("NDVI warmup failed for field={} date={}: {}",
                            fieldId, monthDate, ex.getMessage());
                }
            }
            cursor = cursor.plusMonths(1);
        }
    }

    public byte[] getTilePng(String fieldId, String date, String sceneId, int z, int x, int y) {
        String path = String.format("/tiles/%s/%s/%s/%d/%d/%d.png",
                fieldId, date, sceneId, z, x, y);
        log.debug("NDVI Python upstream GET {}{}", properties.getPythonBaseUrl(), path);
        byte[] png = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tiles/{field_id}/{date}/{scene_id}/{z}/{x}/{y}.png")
                        .build(fieldId, date, sceneId, z, x, y))
                .accept(MediaType.IMAGE_PNG)
                .retrieve()
                .body(byte[].class);
        log.debug("NDVI Python upstream response: path={} bytes={}",
                path, png != null ? png.length : 0);
        return png;
    }

    public String upstreamDetail(RestClientResponseException ex) {
        return PythonClientSupport.upstreamDetail(ex);
    }

    /** Python отдаёт абсолютный URL — переписываем на путь Java-прокси. */
    public String rewriteTileUrl(String url) {
        return PythonClientSupport.rewriteProxyPath(url, "/tiles/", properties.getPythonBaseUrl());
    }

    private Map<String, Object> rewriteTileUrls(Map<String, Object> body) {
        return PythonClientSupport.rewriteTileUrlInBody(body, "/tiles/", properties.getPythonBaseUrl());
    }
}
