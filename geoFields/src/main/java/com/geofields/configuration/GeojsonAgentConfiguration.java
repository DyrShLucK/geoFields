package com.geofields.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(GeojsonAgentProperties.class)
public class GeojsonAgentConfiguration {

    @Bean
    RestClient geojsonAgentRestClient(GeojsonAgentProperties properties, HttpClient geojsonAgentHttpClient) {
        String baseUrl = properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        HttpClient httpClient = geojsonAgentHttpClient;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    HttpClient geojsonAgentHttpClient() {
        // uvicorn/FastAPI на plain HTTP не принимает h2c-upgrade от JDK HttpClient (HTTP/2 по умолчанию).
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }
}
