package com.geofields.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofields.dto.imports.ShapefileImportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Импорт полей из ZIP-архива с shapefile.
 *
 * <p>Архив один раз отправляется во внешний AI-сервис, который возвращает готовый
 * GeoJSON (поля + истории посевов + справочник культур). Больше обращений к внешнему
 * сервису нет — последующая проверка и сохранение выполняются на стороне Java.</p>
 *
 * <p><b>Заглушка:</b> вместо реального вызова внешнего сервиса возвращается пример
 * ответа из {@code static/result.geojson}. Реальный вызов нужно добавить в
 * {@link #callExternalAiService(MultipartFile)} вместо чтения файла-примера.</p>
 */
@Service
public class ShapefileImportService {

    private static final Logger log = LoggerFactory.getLogger(ShapefileImportService.class);

    /** Пример ответа внешнего сервиса (используется заглушкой). */
    private static final String STUB_RESPONSE_RESOURCE = "static/result.geojson";

    private final ObjectMapper objectMapper;

    public ShapefileImportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Принимает ZIP-архив пользователя и возвращает распознанные поля.
     * Это единственная точка обращения к внешнему сервису.
     */
    public ShapefileImportResponse importFromArchive(MultipartFile archive) {
        validateArchive(archive);
        return callExternalAiService(archive);
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

    /**
     * Единственный вызов внешнего AI-сервиса. Сейчас — заглушка, читающая пример ответа.
     * Замените тело метода на реальный HTTP-вызов, сохранив контракт {@link ShapefileImportResponse}.
     */
    private ShapefileImportResponse callExternalAiService(MultipartFile archive) {
        log.info("Импорт shapefile: '{}' ({} байт). Используется заглушка ответа внешнего сервиса.",
                archive.getOriginalFilename(), archive.getSize());
        try (InputStream in = new ClassPathResource(STUB_RESPONSE_RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, ShapefileImportResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать пример ответа импорта: " + STUB_RESPONSE_RESOURCE, e);
        }
    }
}
