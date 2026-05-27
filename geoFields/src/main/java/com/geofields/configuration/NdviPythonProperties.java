package com.geofields.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geofields.ndvi")
public class NdviPythonProperties {

    private String pythonBaseUrl = "http://localhost:8001";

    public String getPythonBaseUrl() {
        return pythonBaseUrl;
    }

    public void setPythonBaseUrl(String pythonBaseUrl) {
        this.pythonBaseUrl = pythonBaseUrl;
    }
}
