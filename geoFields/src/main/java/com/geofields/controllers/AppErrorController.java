package com.geofields.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * Единая HTML-страница для ошибок (404, 500, 403 и т.д.): Spring перенаправляет на {@code /error}.
 * Реализация {@link ErrorController} отключает стандартный {@code BasicErrorController}.
 */
@Controller
public class AppErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public AppErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        WebRequest webRequest = new ServletWebRequest(request);
        Map<String, Object> attrs = errorAttributes.getErrorAttributes(
                webRequest,
                ErrorAttributeOptions.defaults()
                        .including(ErrorAttributeOptions.Include.MESSAGE)
                        .including(ErrorAttributeOptions.Include.BINDING_ERRORS));

        model.addAttribute("status", attrs.get("status"));
        model.addAttribute("error", attrs.get("error"));
        model.addAttribute("message", attrs.get("message"));
        model.addAttribute("path", attrs.get("path"));
        model.addAttribute("timestamp", attrs.get("timestamp"));
        model.addAttribute("trace", attrs.get("trace"));

        return "error";
    }
}
