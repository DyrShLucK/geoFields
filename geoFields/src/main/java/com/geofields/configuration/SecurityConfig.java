package com.geofields.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                || "/get_all_ndvi_tile".equals(uri);
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        JSON_FETCH_ENDPOINTS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/login", "/register").permitAll()
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
                        .requestMatchers(
                                "/org/agronomist",
                                "/org/agronomist/**",
                                "/api/org/agronomist",
                                "/api/org/agronomist/**")
                        .hasAnyRole("AGRONOMIST", "ORG_ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("login")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );
        return http.build();
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
