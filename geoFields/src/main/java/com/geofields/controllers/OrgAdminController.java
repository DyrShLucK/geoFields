package com.geofields.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Админ организации — {@code templates/org-admin.html}; JSON — {@link OrgAdminApiController}. */
@Controller
public class OrgAdminController {

    @GetMapping("/org/admin")
    public String adminPage() {
        return "org-admin";
    }
}
