package com.substring.helpdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        List<String> originPatterns = new ArrayList<>(Arrays.asList(
                "http://localhost:5173",
                "http://localhost:*",
                "https://*.vercel.app"
        ));
        if (allowedOrigins != null) {
            originPatterns.addAll(allowedOrigins);
        }

        config.setAllowedOriginPatterns(originPatterns);

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // OPTIONS is critical for Preflight
        config.setAllowedHeaders(Arrays.asList("*")); // Allow all headers (Content-Type, userEmail, etc.)
        config.setAllowCredentials(true); // Required for sessions/cookies

        source.registerCorsConfiguration("/**", config); // Apply to every single endpoint
        return new CorsFilter(source);
    }
}
