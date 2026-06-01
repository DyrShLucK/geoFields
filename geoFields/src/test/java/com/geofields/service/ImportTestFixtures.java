package com.geofields.service;

import com.geofields.dto.imports.ImportCropItem;
import com.geofields.dto.imports.ImportFieldFeature;
import com.geofields.dto.imports.ImportFieldFeatureCollection;
import com.geofields.dto.imports.ImportFieldHistoryItem;
import com.geofields.dto.imports.ImportFieldProperties;
import com.geofields.dto.imports.ImportGeoJsonGeometry;
import com.geofields.dto.imports.ShapefileImportResponse;
import com.geofields.security.GeoFieldsUserDetails;
import com.geofields.security.UserRole;

import java.util.List;

public final class ImportTestFixtures {

    private ImportTestFixtures() {
    }

    public static GeoFieldsUserDetails agronomistUser() {
        return new GeoFieldsUserDetails(
                501L,
                10L,
                "agronomist",
                "hash",
                UserRole.AGRONOMIST,
                true,
                "Ivanov",
                "Ivan",
                "Ivanovich");
    }

    public static ShapefileImportResponse sampleImportResponse() {
        ImportFieldHistoryItem history = new ImportFieldHistoryItem(
                "Пшеница",
                "2024-04-01",
                "2024-09-15",
                10.5,
                10.0,
                3.2,
                32.0,
                3.0,
                3.1,
                "import",
                "основной сев",
                2024);

        ImportFieldFeature feature = new ImportFieldFeature(
                "Feature",
                new ImportFieldProperties(" Поле А ", 12.3, true, List.of(history)),
                polygonGeometry());

        return new ShapefileImportResponse(
                new ImportFieldFeatureCollection("FeatureCollection", List.of(feature)),
                List.of(new ImportCropItem("Пшеница")));
    }

    public static ImportGeoJsonGeometry polygonGeometry() {
        return new ImportGeoJsonGeometry(
                "Polygon",
                List.of(List.of(
                        List.of(37.0, 55.0),
                        List.of(37.1, 55.0),
                        List.of(37.1, 55.1),
                        List.of(37.0, 55.0))));
    }

    public static String sampleGeometryJson() {
        return "{\"type\":\"Polygon\",\"coordinates\":[[[37.0,55.0],[37.1,55.0],[37.1,55.1],[37.0,55.0]]]}";
    }
}
