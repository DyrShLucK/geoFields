package com.geofields.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geofields.s3")
public class S3StorageProperties {
    private boolean enabled = false;
    private String endpoint = "http://minio:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucketTiles = "geofields-tiles";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketTiles() {
        return bucketTiles;
    }

    public void setBucketTiles(String bucketTiles) {
        this.bucketTiles = bucketTiles;
    }
}

