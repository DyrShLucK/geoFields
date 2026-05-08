package com.geofields.dto.orgmanager;

public record CsrfInfo(String parameterName, String headerName, String token) {
}
