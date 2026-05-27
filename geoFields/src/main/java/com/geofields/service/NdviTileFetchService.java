package com.geofields.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Один запрос к Python на ключ тайла; параллельные запросы MapLibre ждут тот же результат.
 */
@Service
public class NdviTileFetchService {

    private static final Logger log = LoggerFactory.getLogger(NdviTileFetchService.class);

    private final NdviPythonClient ndviPythonClient;
    private final NdviTileCacheService ndviTileCacheService;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public NdviTileFetchService(
            NdviPythonClient ndviPythonClient,
            NdviTileCacheService ndviTileCacheService) {
        this.ndviPythonClient = ndviPythonClient;
        this.ndviTileCacheService = ndviTileCacheService;
    }

    public FetchResult fetchTile(
            String fieldId,
            String date,
            String sceneId,
            int z,
            int x,
            int y) {
        String cacheKey = ndviTileCacheService.cacheKey(fieldId, date, sceneId, z, x, y);

        Optional<NdviTileCacheService.CachedTile> cached = ndviTileCacheService.lookup(cacheKey);
        if (cached.isPresent()) {
            return FetchResult.hit(cached.get());
        }

        Object lock = new Object();
        Object existing = keyLocks.putIfAbsent(cacheKey, lock);
        if (existing != null) {
            lock = existing;
        }
        synchronized (lock) {
            try {
                cached = ndviTileCacheService.lookup(cacheKey);
                if (cached.isPresent()) {
                    return FetchResult.hit(cached.get());
                }

                log.warn("NDVI tile cache MISS → Python: field={} z/x/y={}/{}/{} (первый запрос или MinIO пуст)",
                        fieldId, z, x, y);
                byte[] png = ndviPythonClient.getTilePng(fieldId, date, sceneId, z, x, y);
                if (png == null || png.length == 0) {
                    return FetchResult.emptyResponse();
                }
                ndviTileCacheService.store(cacheKey, png);
                return FetchResult.miss(png);
            } catch (RestClientResponseException | ResourceAccessException ex) {
                log.error("NDVI tile Python failed field={} z/x/y={}/{}/{}: {}",
                        fieldId, z, x, y, ex.getMessage());
                throw ex;
            } finally {
                keyLocks.remove(cacheKey, lock);
            }
        }
    }

    public record FetchResult(byte[] png, NdviTileCacheService.CacheSource source, boolean cacheHit, boolean noContent) {
        static FetchResult hit(NdviTileCacheService.CachedTile tile) {
            return new FetchResult(tile.data(), tile.source(), true, false);
        }

        static FetchResult miss(byte[] png) {
            return new FetchResult(png, null, false, false);
        }

        static FetchResult emptyResponse() {
            return new FetchResult(new byte[0], null, false, true);
        }
    }
}
