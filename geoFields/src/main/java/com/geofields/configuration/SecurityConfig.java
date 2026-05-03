package com.geofields.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

// Правила доступа к URL — отдельно от кодирования паролей и от загрузки пользователя из БД.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()
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
}
