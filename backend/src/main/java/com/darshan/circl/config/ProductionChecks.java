package com.darshan.circl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start the prod profile with development settings: the secrets committed for local use,
 * short secrets, a wildcard or plain-http CORS origin, or the Docker Compose database password.
 */
@Component
@Profile("prod")
public class ProductionChecks {

    public ProductionChecks(@Value("${circl.jwt.secret}") String jwtSecret,
                            @Value("${circl.checkin.secret}") String checkinSecret,
                            @Value("${circl.cors.allowed-origins}") List<String> corsOrigins,
                            @Value("${circl.auth.cookie-secure}") boolean cookieSecure,
                            @Value("${spring.datasource.password}") String dbPassword) {
        List<String> problems = new ArrayList<>();
        checkSecret("CIRCL_JWT_SECRET", jwtSecret, problems);
        checkSecret("CIRCL_CHECKIN_SECRET", checkinSecret, problems);
        if (jwtSecret.equals(checkinSecret)) {
            problems.add("CIRCL_JWT_SECRET and CIRCL_CHECKIN_SECRET must be different");
        }
        if (corsOrigins.isEmpty() || corsOrigins.stream().anyMatch(o -> o.contains("*") || !o.startsWith("https://"))) {
            problems.add("CORS_ORIGINS must list the frontend's https:// origins, no wildcards");
        }
        if (!cookieSecure) {
            problems.add("the refresh cookie must be Secure in production");
        }
        if ("circl".equals(dbPassword)) {
            problems.add("DB_PASSWORD is the local Docker Compose password");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with the prod profile: " + String.join("; ", problems));
        }
    }

    private static void checkSecret(String name, String value, List<String> problems) {
        if (value.contains("dev-only") || value.contains("change-me")) {
            problems.add(name + " is the development default");
        } else if (value.getBytes(StandardCharsets.UTF_8).length < 32) {
            problems.add(name + " must be at least 32 bytes");
        }
    }
}
