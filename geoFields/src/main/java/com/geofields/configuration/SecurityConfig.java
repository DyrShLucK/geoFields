package com.geofields.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

// Правила доступа к URL - отдельно от кодирования паролей и от загрузки пользователя из БД.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final RequestMatcher JSON_FETCH_ENDPOINTS = request -> {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/")) {
            return true;
        }
        return "/get_fields".equals(uri)
                || "/get_ndvi_value".equals(uri)
                || "/get_ndvi_by_id".equals(uri)
                || "/get_all_ndvi_tile".equals(uri)
                || "/get_ndvi_tiles_for_field".equals(uri)
                || "/get_ndvi_trend".equals(uri)
                || "/get_slope_tiles_for_field".equals(uri)
                || "/get_slope_trend".equals(uri)
                || uri.startsWith("/prepare_elevation/")
                || uri.startsWith("/slope/tiles/")
                || uri.startsWith("/tiles/elevation/")
                || uri.startsWith("/tiles/slope/")
                || uri.startsWith("/tiles/");
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                // Принудительно материализуем CSRF-токен на каждом запросе, чтобы cookie
                // XSRF-TOKEN всегда выставлялся (иначе первый POST со страницы, делавшей
                // только GET-запросы, например импорт с карты, падает с 403).
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        JSON_FETCH_ENDPOINTS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/login", "/register", "/about").permitAll()
                        .requestMatchers(
                                "/org/admin",
                                "/org/admin/**",
                                "/api/org/admin",
                                "/api/org/admin/**")
                        .hasRole("ORG_ADMIN")
                        .requestMatchers(
                                "/org/manager",
                                "/org/manager/**",
                                "/api/org/manager",
                                "/api/org/manager/**")
                        .hasAnyRole("ORG_MANAGER", "ORG_ADMIN")
                        // Импорт полей — только агроном/админ
                        .requestMatchers("/api/fields/import/**")
                        .hasAnyRole("AGRONOMIST", "ORG_ADMIN")
                        // Создание/изменение/удаление севооборота и операций — агроном/админ
                        .requestMatchers(HttpMethod.POST, "/api/org/agronomist/**", "/api/field-work/**")
                        .hasAnyRole("AGRONOMIST", "ORG_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/org/agronomist/**", "/api/field-work/**")
                        .hasAnyRole("AGRONOMIST", "ORG_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/org/agronomist/**", "/api/field-work/**")
                        .hasAnyRole("AGRONOMIST", "ORG_ADMIN")
                        // Страницы-редакторы (севооборот и операции) — агроном/админ
                        .requestMatchers(
                                "/org/agronomist",
                                "/org/agronomist/**",
                                "/org/field-work",
                                "/org/field-work/**")
                        .hasAnyRole("AGRONOMIST", "ORG_ADMIN")
                        // Просмотр (GET) операций, истории и отчётов — доступно и обычному пользователю
                        .requestMatchers(HttpMethod.GET,
                                "/api/org/agronomist/**",
                                "/api/field-work/**",
                                "/api/reports/**")
                        .hasAnyRole("USER", "AGRONOMIST", "ORG_ADMIN")
                        .requestMatchers(
                                "/org/reports",
                                "/org/reports/**",
                                "/api/reports",
                                "/api/reports/**")
                        .hasAnyRole("USER", "AGRONOMIST", "ORG_ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/about")
                        .loginProcessingUrl("/login")
                        .failureUrl("/login?error")
                        .usernameParameter("login")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/about?logout")
                        .permitAll()
                );
        return http.build();
    }

    /**
     * Обращается к отложенному CSRF-токену, заставляя {@link CookieCsrfTokenRepository}
     * записать cookie XSRF-TOKEN в ответ ещё до выполнения каких-либо POST-запросов.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "null"));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        config.setAllowedHeaders(List.of("*"));

        config.setAllowCredentials(true);

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
