package com.geofields.service;

import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PythonClientSupport {

    private PythonClientSupport() {
    }

    public static String upstreamDetail(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body != null && !body.isBlank() ? body : ex.getMessage();
    }

    public static String rewriteProxyPath(String url, String pathPrefix, String pythonBaseUrl) {
        if (url == null || url.isBlank()) {
            return url;
        }
        int idx = url.indexOf(pathPrefix);
        if (idx >= 0) {
            return url.substring(idx);
        }
        String base = pythonBaseUrl.trim().replaceAll("/$", "");
        if (url.startsWith(base)) {
            return url.substring(base.length());
        }
        return url;
    }

    public static Map<String, Object> rewriteTileUrlInBody(
            Map<String, Object> body,
            String pathPrefix,
            String pythonBaseUrl) {
        if (body == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(body);
        Object url = result.get("url");
        if (url instanceof String s) {
            result.put("url", rewriteProxyPath(s, pathPrefix, pythonBaseUrl));
        }
        return result;
    }

    /** Переписывает несколько URL-полей ответа Python на относительные пути прокси Java. */
    public static Map<String, Object> rewritePathsInBody(
            Map<String, Object> body,
            String pythonBaseUrl,
            Map<String, String> jsonFieldToPathPrefix) {
        if (body == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(body);
        jsonFieldToPathPrefix.forEach((field, prefix) -> {
            Object value = result.get(field);
            if (value instanceof String s) {
                result.put(field, rewriteProxyPath(s, prefix, pythonBaseUrl));
            }
        });
        return result;
    }
}

