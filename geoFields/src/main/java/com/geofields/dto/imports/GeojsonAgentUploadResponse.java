package com.geofields.dto.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeojsonAgentUploadResponse(
        String job_id,
        String message,
        String status
) {
}
