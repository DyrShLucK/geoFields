package com.geofields.controllers;

import com.geofields.model.Crops;
import com.geofields.model.Field_crops;
import com.geofields.model.Fields;
import com.geofields.service.ShapefileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final ShapefileService shapefileService;

    @GetMapping("/")
    public String mainPage(Model model) {
        try {
            // Получаем границы шейп-файла для начального зума
            Map<String, Object> bounds = shapefileService.getShapefileBounds();
            model.addAttribute("bounds", bounds);
            return "map";
        } catch (IOException e) {
            model.addAttribute("error", "Ошибка загрузки шейп-файла: " + e.getMessage());
            return "map";
        }
    }

    @GetMapping("/api/shapefile")
    @ResponseBody
    public ResponseEntity<?> getShapefileAsGeoJSON() {
        try {
            String geoJSON = shapefileService.convertToGeoJSON();
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(geoJSON);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка чтения шейп-файла: " + e.getMessage()));
        }
    }

    @GetMapping("/api/shapefile/bounds")
    @ResponseBody
    public ResponseEntity<?> getBounds() {
        try {
            return ResponseEntity.ok(shapefileService.getShapefileBounds());
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка получения границ: " + e.getMessage()));
        }
    }

}
