package com.geofields.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Страница агротехнических операций — {@code templates/org-field-work.html}. */
@Controller
public class FieldWorkPageController {

    @GetMapping("/org/field-work")
    public String fieldWorkPage() {
        return "org-field-work";
    }
}
