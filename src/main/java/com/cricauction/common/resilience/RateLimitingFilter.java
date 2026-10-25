package com.cricauction.common.resilience;

import com.cricauction.common.api.ApiError;
import com.cricauction.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Check if rate limiting is bypassed via internal test header or health checks
        String bypass = request.getHeader("X-Bypass-Rate-Limit");
        String uri = request.getRequestURI();

        if ("true".equalsIgnoreCase(bypass) || uri.contains("/actuator") || uri.contains("/swagger-ui") || uri.contains("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        boolean allowed = rateLimiter.tryAcquire(clientIp, uri);

        if (!allowed) {
            long retryAfter = rateLimiter.getRetryAfterSeconds(clientIp, uri);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setHeader("X-Rate-Limit-Remaining", "0");

            ApiError error = ApiError.of("RATE_LIMIT_EXCEEDED",
                    "Too many requests from this IP. Please wait " + retryAfter + " seconds before retrying.");
            ApiResponse<Void> apiResponse = ApiResponse.error(error);

            response.getOutputStream().println(objectMapper.writeValueAsString(apiResponse));
            return;
        }

        int remaining = rateLimiter.getRemainingTokens(clientIp, uri);
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
