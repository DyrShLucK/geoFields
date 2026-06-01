package com.geofields.service;

import com.geofields.configuration.S3StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class NdviTileCacheService {

    private static final Logger log = LoggerFactory.getLogger(NdviTileCacheService.class);

    /** Пустой прозрачный PNG из Python (tiler.get_empty_bytes) — ~334 байта. */
    private static final int EMPTY_PNG_MAX_BYTES = 512;

    /** Только «настоящие» тайлы в RAM; пустые — только MinIO. */
    private static final int MEMORY_CACHE_MAX = 16_384;

    public enum CacheSource {
        MEMORY, MINIO
    }

    public record CachedTile(byte[] data, CacheSource source) {}

    private final MinioClient minioClient;
    private final S3StorageProperties props;
    private final Map<String, byte[]> memoryCache = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MEMORY_CACHE_MAX;
        }
    };

    public NdviTileCacheService(MinioClient minioClient, S3StorageProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    @PostConstruct
    void initBucket() {
        log.info("NDVI tile cache: memory max={}, only tiles > {} bytes go to RAM (empty PNG ~334b → MinIO only)",
                MEMORY_CACHE_MAX, EMPTY_PNG_MAX_BYTES);
        if (!minioEnabled()) {
            log.warn("NDVI tile cache: MinIO disabled — повторные запросы снова пойдут в Python");
            return;
        }
        ensureBucket();
        verifyMinioRoundTrip();
        log.info("NDVI tile cache: MinIO enabled, endpoint={}, bucket={}",
                props.getEndpoint(), props.getBucketTiles());
    }

    public boolean minioEnabled() {
        return props.isEnabled();
    }

    public String cacheKeyRaw(String fieldId, String date, String sceneId, int z, int x, int y) {
        return fieldId + "/" + date + "/" + sceneId + "/" + z + "/" + x + "/" + y;
    }

    public String cacheKey(String fieldId, String date, String sceneId, int z, int x, int y) {
        String raw = cacheKeyRaw(fieldId, date, sceneId, z, x, y);
        String hash = sha256Hex(raw);
        return "tiles/" + hash.substring(0, 2) + "/" + hash + ".png";
    }

    /** Ключ S3/MinIO для тайлов рельефа и уклона (SRTM). */
    public String terrainCacheKey(String layer, String fieldId, int z, int x, int y) {
        return "terrain/" + layer + "/" + fieldId + "/" + z + "/" + x + "/" + y + ".png";
    }

    public static boolean isRealTilePng(byte[] png) {
        return png != null && png.length > EMPTY_PNG_MAX_BYTES;
    }

    /** Кэширует только непустые PNG (рельеф/уклон). */
    public void storeIfReal(String key, byte[] png) {
        if (!isRealTilePng(png)) {
            log.debug("Terrain tile cache: skip empty PNG {} ({} bytes)", key, png != null ? png.length : 0);
            return;
        }
        store(key, png);
    }

    public Optional<CachedTile> lookup(String key) {
        synchronized (memoryCache) {
            byte[] mem = memoryCache.get(key);
            if (mem != null) {
                log.debug("NDVI tile cache HIT memory: {} ({} bytes)", key, mem.length);
                return Optional.of(new CachedTile(mem, CacheSource.MEMORY));
            }
        }

        if (!minioEnabled()) {
            return Optional.empty();
        }

        byte[] fromMinio = getFromMinio(key);
        if (fromMinio != null) {
            if (isRealTilePng(fromMinio)) {
                putMemory(key, fromMinio);
            }
            log.info("NDVI tile cache HIT minio: {} ({} bytes)", key, fromMinio.length);
            return Optional.of(new CachedTile(fromMinio, CacheSource.MINIO));
        }

        return Optional.empty();
    }

    public void store(String key, byte[] png) {
        if (png == null) {
            return;
        }
        if (isRealTilePng(png)) {
            putMemory(key, png);
            log.debug("NDVI tile cache PUT memory: {} ({} bytes)", key, png.length);
        } else {
            log.debug("NDVI tile cache: skip memory for empty tile {} ({} bytes), MinIO only",
                    key, png.length);
        }
        if (minioEnabled()) {
            putToMinio(key, png);
        }
    }

    private void putMemory(String key, byte[] png) {
        synchronized (memoryCache) {
            memoryCache.put(key, png);
        }
    }

    private byte[] getFromMinio(String key) {
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(props.getBucketTiles())
                .object(key)
                .build())) {
            return readAllBytes(is);
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() != null ? e.errorResponse().code() : "unknown";
            if (!"NoSuchKey".equals(code)) {
                log.warn("NDVI tile cache MinIO read error for {}: {}", key, code);
            }
            return null;
        } catch (Exception e) {
            log.warn("NDVI tile cache MinIO read failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void putToMinio(String key, byte[] png) {
        ensureBucket();
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucketTiles())
                    .object(key)
                    .contentType("image/png")
                    .stream(in, png.length, -1)
                    .build());
            log.debug("NDVI tile cache PUT minio: {} ({} bytes)", key, png.length);
        } catch (Exception e) {
            log.warn("NDVI tile cache MinIO PUT failed for {}: {}", key, e.getMessage());
        }
    }

    private void verifyMinioRoundTrip() {
        String probeKey = "tiles/__probe__/healthcheck.bin";
        byte[] payload = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            putToMinio(probeKey, payload);
            byte[] back = getFromMinio(probeKey);
            if (back != null && back.length == payload.length) {
                log.info("NDVI tile cache: MinIO read/write probe OK");
            } else {
                log.error("NDVI tile cache: MinIO read/write probe FAILED");
            }
        } catch (Exception e) {
            log.error("NDVI tile cache: MinIO probe error: {}", e.getMessage());
        }
    }

    private void ensureBucket() {
        if (!minioEnabled()) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(props.getBucketTiles())
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(props.getBucketTiles())
                        .build());
                log.info("Created MinIO bucket: {}", props.getBucketTiles());
            }
        } catch (Exception e) {
            log.error("Cannot ensure MinIO bucket {} at {}: {}",
                    props.getBucketTiles(), props.getEndpoint(), e.getMessage());
        }
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) >= 0) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
