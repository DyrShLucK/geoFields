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
        return "index";
    }
}
