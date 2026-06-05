package com.geofields.dto.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeojsonAgentJobStatus(
        String job_id,
        String status,
        String progress,
        String error,
        String result_url
) {
    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status);
    }
}
