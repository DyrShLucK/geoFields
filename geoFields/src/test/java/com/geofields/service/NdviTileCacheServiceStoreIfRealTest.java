package com.geofields.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NdviTileCacheServiceStoreIfRealTest {

    @Mock
    private io.minio.MinioClient minioClient;

    private NdviTileCacheService cacheService;

    @BeforeEach
    void setUp() {
        com.geofields.configuration.S3StorageProperties props = new com.geofields.configuration.S3StorageProperties();
        props.setEnabled(false);
        cacheService = new NdviTileCacheService(minioClient, props);
    }

    @Test
    void storeIfReal_putsRealTileInMemory() {
        byte[] png = new byte[800];
        cacheService.storeIfReal("terrain/elevation/1/10/1/2.png", png);

        assertThat(cacheService.lookup("terrain/elevation/1/10/1/2.png")).isPresent();
    }

    @Test
    void storeIfReal_skipsEmptyTile() {
        byte[] empty = new byte[334];
        cacheService.storeIfReal("terrain/slope/1/10/1/2.png", empty);

        assertThat(cacheService.lookup("terrain/slope/1/10/1/2.png")).isEmpty();
    }

    @Test
    void terrainCacheKey_usesStablePath() {
        assertThat(cacheService.terrainCacheKey("elevation", "5", 12, 100, 200))
                .isEqualTo("terrain/elevation/5/12/100/200.png");
    }
}
