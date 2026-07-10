package com.staydesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                   .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                   .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                   .authorizeHttpRequests(auth -> auth
                           .requestMatchers("/auth/employee/login", "/error", "/stripe/connect/return", "/stripe/connect/refresh").permitAll()
                           .requestMatchers("/webhooks/sifely/**").permitAll()
                           .requestMatchers("/actuator/health").permitAll()
                           .requestMatchers(HttpMethod.POST, "/guests/*/flag").hasAnyRole("ADMIN", "MANAGER")
                           .requestMatchers(HttpMethod.DELETE, "/guests/*/flag").hasAnyRole("ADMIN", "MANAGER")
                           .requestMatchers(HttpMethod.POST, "/guests/*/legal-hold").hasAnyRole("ADMIN", "MANAGER")
                           .requestMatchers(HttpMethod.DELETE, "/guests/*/legal-hold").hasAnyRole("ADMIN", "MANAGER")
                           .requestMatchers(HttpMethod.POST, "/reservations/*/legal-hold").hasAnyRole("ADMIN", "MANAGER")
                           .requestMatchers(HttpMethod.DELETE, "/reservations/*/legal-hold").hasAnyRole("ADMIN", "MANAGER")
                           .requestMatchers(HttpMethod.GET, "/lock-passcodes/reservation/**").hasAnyRole("ADMIN", "MANAGER", "FRONT_DESK")
                           .requestMatchers("/lock-passcodes/**").hasAnyRole("ADMIN", "FRONT_DESK")
                           .requestMatchers("/admin/**", "/reports/**")
                           .hasRole("ADMIN")
                           .anyRequest().authenticated())
                   .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                   .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> appMetadata = jwt.getClaim("app_metadata");

            if (appMetadata == null) {
                return List.of();
            }

            String role = (String) appMetadata.get("role");


            if (role == null) {
                return List.of();
            }

            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });

        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                               .jwsAlgorithm(SignatureAlgorithm.ES256)
                               .build();
    }
}
