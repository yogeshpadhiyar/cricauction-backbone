package com.cricauction.security;

import com.cricauction.config.JwtConfig;
import com.cricauction.modules.auth.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;
    private SecretKey signingKey;
    private JwtParser jwtParser;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser().verifyWith(this.signingKey).build();
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getAccessTokenExpirationMs());

        List<String> roleNames = user.getRoleNames();
        String primaryRole = roleNames.isEmpty() ? "PLAYER" : roleNames.get(0).toUpperCase();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("mobile", user.getMobile())
                .claim("role", primaryRole)
                .claim("roles", roleNames)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(this.signingKey)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("role", String.class);
    }

    public Claims getClaimsFromToken(String token) {
        return this.jwtParser.parseSignedClaims(token).getPayload();
    }

    public Claims validateAndGetClaims(String token) {
        try {
            return this.jwtParser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        return validateAndGetClaims(token) != null;
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtConfig.getAccessTokenExpirationMs() / 1000;
    }
}
