package com.geofields.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Страница агронома — {@code templates/org-agronomist.html}; API — {@link OrgAgronomistApiController}. */
@Controller
public class OrgAgronomistController {

    @GetMapping("/org/agronomist")
    public String agronomistPage() {
        return "org-agronomist";
    }
}
