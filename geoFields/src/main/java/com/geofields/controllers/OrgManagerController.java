package com.geofields.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Страница менеджера — {@code templates/org-manager.html}; JSON — {@link OrgManagerApiController}.
 */
@Controller
public class OrgManagerController {

    @GetMapping("/org/manager")
    public String managerPage() {
        return "org-manager";
    }
}
