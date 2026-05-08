package com.geofields.service;

import lombok.RequiredArgsConstructor;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.data.SimpleFeatureStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.styling.SLD;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ShapefileService {

    private SimpleFeatureCollection featureCollection;
    private ReferencedEnvelope bounds;

    /**
     * Конвертирует шейп-файл из ZIP архива в GeoJSON
     */
    public String convertToGeoJSON() throws IOException {
        if (featureCollection == null) {
            loadShapefile();
        }

        FeatureJSON fjson = new FeatureJSON();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fjson.writeFeatureCollection(featureCollection, out);
            return out.toString("UTF-8");
        }
    }

    /**
     * Получает границы шейп-файла
     */
    public Map<String, Object> getShapefileBounds() throws IOException {
        if (bounds == null) {
            loadShapefile();
        }

        Map<String, Object> boundsMap = new HashMap<>();
        boundsMap.put("minX", bounds.getMinX());
        boundsMap.put("minY", bounds.getMinY());
        boundsMap.put("maxX", bounds.getMaxX());
        boundsMap.put("maxY", bounds.getMaxY());

        // Центр для начального отображения
        boundsMap.put("centerX", (bounds.getMinX() + bounds.getMaxX()) / 2);
        boundsMap.put("centerY", (bounds.getMinY() + bounds.getMaxY()) / 2);

        return boundsMap;
    }

    /**
     * Загружает шейп-файл из ZIP архива в папке static
     */
    private void loadShapefile() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/poly2.zip");

        if (!resource.exists()) {
            throw new IOException("Файл poly.zip не найден в папке static");
        }

        // Создаем временную директорию
        Path tempDir = Files.createTempDirectory("shapefile");

        try (InputStream inputStream = resource.getInputStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            // Распаковываем ZIP
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path filePath = tempDir.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zipInputStream, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }

        // Ищем .shp файл
        File shpFile = Files.walk(tempDir)
                .map(Path::toFile)
                .filter(f -> f.getName().toLowerCase().endsWith(".shp"))
                .findFirst()
                .orElseThrow(() -> new IOException("SHM файл не найден в архиве"));

        // Открываем шейп-файл
        ShapefileDataStore dataStore = new ShapefileDataStore(shpFile.toURI().toURL());
        dataStore.setCharset(java.nio.charset.Charset.forName("UTF-8"));

        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);

        if (featureSource instanceof SimpleFeatureStore) {
            featureCollection = featureSource.getFeatures();
            bounds = featureCollection.getBounds();
        } else {
            throw new IOException("Источник данных не поддерживает чтение");
        }
    }
}