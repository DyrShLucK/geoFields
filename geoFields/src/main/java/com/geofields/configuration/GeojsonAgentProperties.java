package com.geofields.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geofields.geojson-agent")
public class GeojsonAgentProperties {

    private String baseUrl = "http://localhost:6767";
    private long pollIntervalMs = 2_000;
    private long maxWaitMs = 600_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getMaxWaitMs() {
        return maxWaitMs;
    }

    public void setMaxWaitMs(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
    }
}
