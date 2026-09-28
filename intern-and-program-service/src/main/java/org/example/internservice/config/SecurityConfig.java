package org.example.internservice.config;

import lombok.RequiredArgsConstructor;
import org.example.internservice.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Public endpoints cho nộp hồ sơ trực tuyến và upload CV (TM-4, TM-10)
                .requestMatchers(HttpMethod.POST, "/api/interns/apply").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/employees/interns/apply").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/interns").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/employees/interns").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/interns/*/documents").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/employees/interns/*/documents").permitAll()
                // Internal callback cho reporting-and-integration-service
                .requestMatchers(HttpMethod.PATCH, "/api/interns/*/email-status").permitAll()
                // Public endpoint cho onboarding activation (TM-12)
                .requestMatchers("/api/onboarding/**").permitAll()
                // Public endpoints cho chương trình thực tập đang mở tuyển (TM-10)
                .requestMatchers(HttpMethod.GET, "/api/programs/open").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/programs/{id:[0-9]+}").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
