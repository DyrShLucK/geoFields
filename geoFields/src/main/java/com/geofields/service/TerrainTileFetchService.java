package com.geofields.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш PNG-тайлов рельефа/уклона (SRTM) в MinIO через {@link NdviTileCacheService}.
 * В S3 попадают только непустые тайлы ({@link NdviTileCacheService#isRealTilePng}).
 */
@Service
public class TerrainTileFetchService {

    private static final Logger log = LoggerFactory.getLogger(TerrainTileFetchService.class);

    private final ElevationPythonClient elevationPythonClient;
    private final NdviTileCacheService tileCacheService;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public TerrainTileFetchService(
            ElevationPythonClient elevationPythonClient,
            NdviTileCacheService tileCacheService) {
        this.elevationPythonClient = elevationPythonClient;
        this.tileCacheService = tileCacheService;
    }

    public NdviTileFetchService.FetchResult fetchElevationTile(String fieldId, int z, int x, int y) {
        return fetchTile("elevation", fieldId, z, x, y,
                () -> elevationPythonClient.getElevationTilePng(fieldId, z, x, y));
    }

    public NdviTileFetchService.FetchResult fetchSlopeTile(String fieldId, int z, int x, int y) {
        return fetchTile("slope", fieldId, z, x, y,
                () -> elevationPythonClient.getTerrainSlopeTilePng(fieldId, z, x, y));
    }

    private NdviTileFetchService.FetchResult fetchTile(
            String layer,
            String fieldId,
            int z,
            int x,
            int y,
            TileFetcher fetcher) {
        String cacheKey = tileCacheService.terrainCacheKey(layer, fieldId, z, x, y);

        Optional<NdviTileCacheService.CachedTile> cached = tileCacheService.lookup(cacheKey);
        if (cached.isPresent()) {
            return NdviTileFetchService.FetchResult.hit(cached.get());
        }

        Object lock = new Object();
        Object existing = keyLocks.putIfAbsent(cacheKey, lock);
        if (existing != null) {
            lock = existing;
        }
        synchronized (lock) {
            try {
                cached = tileCacheService.lookup(cacheKey);
                if (cached.isPresent()) {
                    return NdviTileFetchService.FetchResult.hit(cached.get());
                }

                log.debug("Terrain tile cache MISS → Python: layer={} field={} z/x/y={}/{}/{}",
                        layer, fieldId, z, x, y);
                byte[] png = fetcher.fetch();
                if (!NdviTileCacheService.isRealTilePng(png)) {
                    return NdviTileFetchService.FetchResult.emptyResponse();
                }
                tileCacheService.storeIfReal(cacheKey, png);
                return NdviTileFetchService.FetchResult.miss(png);
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 204) {
                    return NdviTileFetchService.FetchResult.emptyResponse();
                }
                throw ex;
            } catch (ResourceAccessException ex) {
                log.error("Terrain tile Python failed layer={} field={} z/x/y={}/{}/{}: {}",
                        layer, fieldId, z, x, y, ex.getMessage());
                throw ex;
            } finally {
                keyLocks.remove(cacheKey, lock);
            }
        }
    }

    @FunctionalInterface
    private interface TileFetcher {
        byte[] fetch();
    }
}
