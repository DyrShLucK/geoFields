package com.geofields.controllers;

import com.geofields.service.ShapefileService;
import com.geofields.service.StubAuthContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final ShapefileService shapefileService;
    private final StubAuthContextService stubAuthContextService;

    @GetMapping("/")
    public String mainPage(Model model) {
        model.addAttribute("stubUserId", stubAuthContextService.getCurrentUserId());
        model.addAttribute("stubOrgId", stubAuthContextService.getCurrentOrganizationId());
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam String login) {
        // Заглушка: фактическая авторизация пока не подключена.
        return "redirect:/";
    }

    @PostMapping("/register")
    public String registerSubmit(@RequestParam String login, @RequestParam String password) {
        // Заглушка: регистрация будет реализована после подключения security.
        return "redirect:/login";
    }
}
