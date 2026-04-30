package com.example.exam.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final String secret;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtService(@Value("${app.jwt-secret}") String secret) {
        this.secret = secret;
    }

    public String createToken(Long userId, String username) {
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            String payload = encodeJson(Map.of(
                    "sub", String.valueOf(userId),
                    "username", username,
                    "exp", Instant.now().plusSeconds(60 * 60 * 24).getEpochSecond()
            ));
            String signingInput = header + "." + payload;
            return signingInput + "." + sign(signingInput);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create token", ex);
        }
    }

    public JwtPrincipal parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String signingInput = parts[0] + "." + parts[1];
            if (!sign(signingInput).equals(parts[2])) {
                return null;
            }
            Map<?, ?> payload = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                return null;
            }
            Long userId = Long.valueOf(String.valueOf(payload.get("sub")));
            String username = String.valueOf(payload.get("username"));
            return new JwtPrincipal(userId, username);
        } catch (Exception ex) {
            return null;
        }
    }

    private String encodeJson(Map<String, ?> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
