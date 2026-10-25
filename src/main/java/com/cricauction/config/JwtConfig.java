package com.cricauction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cricauction.security.jwt")
public class JwtConfig {
    private String secret;
    private long accessTokenExpirationMs = 86400000L; // 24 hours
    private long refreshTokenExpirationMs = 604800000L; // 7 days
}
