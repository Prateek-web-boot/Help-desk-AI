package com.substring.helpdesk.RateLimiter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();


    private Bucket createNewBucket() {

        return Bucket.builder()
                .addLimit(Bandwidth.classic(20,
                        Refill.intervally(20, Duration.ofMinutes(1))))
                .build();
    }


    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        //Get the IP Address
        String ip= request.getHeader("X-FORWARDED-FOR");
        if(ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }

        //resolve the bucket for the specific IP
        Bucket bucket = buckets.computeIfAbsent(ip, k->createNewBucket());


        //try to consume a token
        if(bucket.tryConsume(1)){
            filterChain.doFilter(request, response);
        } else {
            //Reject if empty
            // 1. Manually add CORS headers so Vercel accepts the 429 response
            response.setHeader("Access-Control-Allow-Origin", "https://helpdesk-ai-frontend-green.vercel.app");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "*");
            response.setHeader("Access-Control-Allow-Credentials", "true");

            // 2. Return the 429 status
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too many requests. Please wait a moment.\"}");

        }

    }

}
