package com.geofields.service;

import com.geofields.dto.imports.ShapefileImportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Импорт полей из ZIP-архива с shapefile.
 *
 * <p>Архив один раз отправляется в geojson-agent, который возвращает готовый
 * GeoJSON (поля + истории посевов + справочник культур). Больше обращений к внешнему
 * сервису нет — последующая проверка и сохранение выполняются на стороне Java.</p>
 */
@Service
public class ShapefileImportService {

    private static final Logger log = LoggerFactory.getLogger(ShapefileImportService.class);

    private final GeojsonAgentClient geojsonAgentClient;

    public ShapefileImportService(GeojsonAgentClient geojsonAgentClient) {
        this.geojsonAgentClient = geojsonAgentClient;
    }

    /**
     * Принимает ZIP-архив пользователя и возвращает распознанные поля.
     * Это единственная точка обращения к внешнему сервису.
     */
    public ShapefileImportResponse importFromArchive(MultipartFile archive) {
        validateArchive(archive);
        log.info("Импорт shapefile: '{}' ({} байт) → geojson-agent",
                archive.getOriginalFilename(), archive.getSize());
        return geojsonAgentClient.convertArchive(archive);
    }

    private void validateArchive(MultipartFile archive) {
        if (archive == null || archive.isEmpty()) {
            throw new IllegalArgumentException("Загрузите непустой ZIP-архив с shapefile");
        }
        String name = archive.getOriginalFilename();
        boolean looksLikeZip = (name != null && name.toLowerCase().endsWith(".zip"))
                || "application/zip".equalsIgnoreCase(archive.getContentType())
                || "application/x-zip-compressed".equalsIgnoreCase(archive.getContentType());
        if (!looksLikeZip) {
            throw new IllegalArgumentException("Ожидается ZIP-архив (.zip) с shapefile");
        }
    }
}
