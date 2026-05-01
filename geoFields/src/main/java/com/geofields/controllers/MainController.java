package com.geofields.controllers;

import com.geofields.service.ShapefileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final ShapefileService shapefileService;

    @GetMapping("/")
    public String mainPage(Model model) {
        return "index";
    }
}
