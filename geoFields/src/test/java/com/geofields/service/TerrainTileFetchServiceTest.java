package com.geofields.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerrainTileFetchServiceTest {

    @Mock
    private ElevationPythonClient elevationPythonClient;

    @Mock
    private NdviTileCacheService tileCacheService;

    @InjectMocks
    private TerrainTileFetchService terrainTileFetchService;

    @Test
    void fetchElevationTile_returnsCachedTile_whenMinioHit() {
        byte[] png = new byte[900];
        String key = "terrain/elevation/1/10/100/200.png";
        when(tileCacheService.terrainCacheKey("elevation", "1", 10, 100, 200)).thenReturn(key);
        when(tileCacheService.lookup(key))
                .thenReturn(Optional.of(new NdviTileCacheService.CachedTile(png, NdviTileCacheService.CacheSource.MINIO)));

        NdviTileFetchService.FetchResult result = terrainTileFetchService.fetchElevationTile("1", 10, 100, 200);

        assertThat(result.cacheHit()).isTrue();
        assertThat(result.png()).isEqualTo(png);
        verify(elevationPythonClient, never()).getElevationTilePng(anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void fetchElevationTile_storesOnlyRealPng_whenPythonReturnsData() {
        byte[] png = new byte[1200];
        String key = "terrain/elevation/2/12/300/400.png";
        when(tileCacheService.terrainCacheKey("elevation", "2", 12, 300, 400)).thenReturn(key);
        when(tileCacheService.lookup(key)).thenReturn(Optional.empty());
        when(elevationPythonClient.getElevationTilePng("2", 12, 300, 400)).thenReturn(png);

        NdviTileFetchService.FetchResult result = terrainTileFetchService.fetchElevationTile("2", 12, 300, 400);

        assertThat(result.cacheHit()).isFalse();
        assertThat(result.noContent()).isFalse();
        verify(tileCacheService).storeIfReal(eq(key), eq(png));
    }

    @Test
    void fetchSlopeTile_doesNotStore_whenPythonReturnsEmptyPng() {
        byte[] empty = new byte[334];
        String key = "terrain/slope/3/8/50/60.png";
        when(tileCacheService.terrainCacheKey("slope", "3", 8, 50, 60)).thenReturn(key);
        when(tileCacheService.lookup(key)).thenReturn(Optional.empty());
        when(elevationPythonClient.getTerrainSlopeTilePng("3", 8, 50, 60)).thenReturn(empty);

        NdviTileFetchService.FetchResult result = terrainTileFetchService.fetchSlopeTile("3", 8, 50, 60);

        assertThat(result.noContent()).isTrue();
        verify(tileCacheService, never()).storeIfReal(anyString(), org.mockito.ArgumentMatchers.any());
    }
}
