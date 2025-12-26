package com.substring.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Allow Vercel and Localhost
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:5173",
                "https://helpdesk-ai-frontend-green.vercel.app"
        ));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // OPTIONS is critical for Preflight
        config.setAllowedHeaders(Arrays.asList("*")); // Allow all headers (Content-Type, userEmail, etc.)
        config.setAllowCredentials(true); // Required for sessions/cookies

        source.registerCorsConfiguration("/**", config); // Apply to every single endpoint
        return new CorsFilter(source);
    }
}
