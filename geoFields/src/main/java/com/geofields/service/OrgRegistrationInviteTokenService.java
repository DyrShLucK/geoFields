package com.geofields.service;

import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Генерация одноразовых токенов и ссылок для {@code /register?ref=}. */
@Service
public class OrgRegistrationInviteTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String newToken() {
        byte[] buf = new byte[18];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public String registerRelativeUrl(String token) {
        return "/register?ref=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
