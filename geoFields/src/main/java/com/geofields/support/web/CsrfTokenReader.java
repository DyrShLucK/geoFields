package com.geofields.support.web;

import com.geofields.dto.orgmanager.CsrfInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

/** DRY: чтение CSRF из атрибутов запроса (в т.ч. для JSON API с cookie XSRF). */
public final class CsrfTokenReader {

    private CsrfTokenReader() {
    }

    public static CsrfInfo read(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            token = (CsrfToken) request.getAttribute("_csrf");
        }
        if (token == null) {
            return new CsrfInfo(null, null, null);
        }
        return new CsrfInfo(token.getParameterName(), token.getHeaderName(), token.getToken());
    }
}
