package com.geofields.support.web;

import com.geofields.service.NdviTileFetchService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

/** Единый формат HTTP-ответа для PNG-тайлов (NDVI, рельеф, уклон). */
public final class TileHttpResponses {

    private TileHttpResponses() {
    }

    public static ResponseEntity<byte[]> fromFetchResult(
            NdviTileFetchService.FetchResult result,
            String cacheHeaderName) {
        if (result.noContent()) {
            return ResponseEntity.noContent().build();
        }
        if (result.cacheHit()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .header(cacheHeaderName, "HIT-" + result.source().name())
                    .body(result.png());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .header(cacheHeaderName, "MISS")
                .body(result.png());
    }
}
