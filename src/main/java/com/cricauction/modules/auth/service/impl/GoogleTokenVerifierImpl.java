package com.cricauction.modules.auth.service.impl;

import com.cricauction.common.exception.UnauthorizedException;
import com.cricauction.modules.auth.dto.response.GoogleUserInfo;
import com.cricauction.modules.auth.service.GoogleTokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {

    private final ObjectMapper objectMapper;

    @Value("${cricauction.security.google.client-id:}")
    private String expectedClientId;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(createClientHttpRequestFactory())
            .baseUrl("https://oauth2.googleapis.com")
            .build();

    private static SimpleClientHttpRequestFactory createClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public GoogleUserInfo verifyToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID token cannot be empty.");
        }

        // Fast-path support for test / mock tokens in load tests and automated test suites
        if (idToken.startsWith("test-google-token-") || idToken.startsWith("mock-google-token-")) {
            return parseMockToken(idToken);
        }

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tokeninfo")
                            .queryParam("id_token", idToken.trim())
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);

            // Verify audience if configured
            if (expectedClientId != null && !expectedClientId.isBlank()) {
                String aud = root.path("aud").asText();
                if (!expectedClientId.equals(aud)) {
                    log.warn("Google token audience mismatch. Expected: {}, Actual: {}", expectedClientId, aud);
                    throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google token audience mismatch.");
                }
            }

            String email = root.path("email").asText();
            String sub = root.path("sub").asText();
            boolean emailVerified = root.path("email_verified").asBoolean(false)
                    || "true".equalsIgnoreCase(root.path("email_verified").asText());
            String name = root.path("name").asText(email);
            String picture = root.path("picture").asText(null);

            if (email == null || email.isBlank() || sub == null || sub.isBlank()) {
                throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google token missing required claims (email or sub).");
            }

            return GoogleUserInfo.builder()
                    .sub(sub)
                    .email(email.toLowerCase().trim())
                    .emailVerified(emailVerified)
                    .name(name)
                    .picture(picture)
                    .build();

        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Google token with Google OAuth2 servers: {}", e.getMessage());
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Invalid or expired Google token: " + e.getMessage());
        }
    }

    private GoogleUserInfo parseMockToken(String mockToken) {
        // e.g. "test-google-token-virat@example.com" or "test-google-token-user123"
        String identifier = mockToken.replace("test-google-token-", "").replace("mock-google-token-", "");
        String email = identifier.contains("@") ? identifier : identifier + "@gmail.com";
        String name = identifier.contains("@") ? identifier.substring(0, identifier.indexOf("@")) : identifier;
        String sub = "google_sub_" + Math.abs(identifier.hashCode());

        return GoogleUserInfo.builder()
                .sub(sub)
                .email(email.toLowerCase().trim())
                .emailVerified(true)
                .name(capitalize(name))
                .picture("https://lh3.googleusercontent.com/a/default-user")
                .build();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "User";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
