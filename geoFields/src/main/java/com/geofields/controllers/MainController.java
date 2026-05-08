package com.geofields.controllers;

import com.geofields.exception.RegistrationException;
import com.geofields.service.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final UserRegistrationService userRegistrationService;

    /** Главная — статический шаблон; данные пользователя с клиента: {@code GET /api/session/context}. */
    @GetMapping("/")
    public String mainPage() {
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(
            @RequestParam(name = "ref", required = false) String ref,
            Model model,
            HttpServletRequest request) {
        applyRegisterPageModel(ref, model, request);
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(
            @RequestParam String login,
            @RequestParam String email,
            @RequestParam String lastName,
            @RequestParam String firstName,
            @RequestParam String middleName,
            @RequestParam String inviteToken,
            @RequestParam String password,
            @RequestParam String passwordConfirm,
            RedirectAttributes redirectAttributes) {
        try {
            userRegistrationService.registerWithInvite(
                    login, email, lastName, firstName, middleName, password, passwordConfirm, inviteToken);
            return "redirect:/login?pending";
        } catch (RegistrationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("inviteToken", inviteToken);
            redirectAttributes.addFlashAttribute("lastName", lastName);
            redirectAttributes.addFlashAttribute("firstName", firstName);
            redirectAttributes.addFlashAttribute("middleName", middleName);
            redirectAttributes.addFlashAttribute("login", login);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/register";
        }
    }

    private static void applyRegisterPageModel(String ref, Model model, HttpServletRequest request) {
        String invite = ref != null ? ref : "";
        Map<String, ?> flash = RequestContextUtils.getInputFlashMap(request);
        if (flash != null) {
            if (flash.get("errorMessage") instanceof String err) {
                model.addAttribute("errorMessage", err);
            }
            if (flash.get("inviteToken") instanceof String t) {
                invite = t;
            }
            putFlashString(model, flash, "lastName");
            putFlashString(model, flash, "firstName");
            putFlashString(model, flash, "middleName");
            putFlashString(model, flash, "login");
            putFlashString(model, flash, "email");
        }
        model.addAttribute("inviteToken", invite);
        for (String k : new String[] {"lastName", "firstName", "middleName", "login", "email"}) {
            if (!model.containsAttribute(k)) {
                model.addAttribute(k, "");
            }
        }
    }

    private static void putFlashString(Model model, Map<String, ?> flash, String key) {
        if (flash.get(key) instanceof String s) {
            model.addAttribute(key, s);
        }
    }
}
