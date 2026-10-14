package org.example.fileservice.config;

import lombok.RequiredArgsConstructor;
import org.example.fileservice.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration cho file-service.
 * <p>
 * - Actuator endpoints: yêu cầu authority SYSTEM_AUDIT_VIEW hoặc role ADMIN
 * - Internal API (/api/storage/**): cho phép tất cả (gọi nội bộ giữa services)
 * - Mọi request khác: authenticated
 */
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
                // Actuator health/info: chỉ ADMIN hoặc có quyền SYSTEM_AUDIT_VIEW
                .requestMatchers("/actuator/**")
                    .hasAnyAuthority("SYSTEM_AUDIT_VIEW", "ROLE_ADMIN")
                // Internal storage API: không yêu cầu auth (gọi nội bộ qua Eureka)
                .requestMatchers("/api/storage/**").permitAll()
                // Mọi request khác cần authenticated
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
