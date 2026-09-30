package com.darshan.circl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionChecksTest {

    private static final String GOOD_JWT = "jwt-secret-for-this-test-0123456789abcdef";
    private static final String GOOD_CHECKIN = "checkin-secret-for-this-test-0123456789ab";

    private final ApplicationContextRunner prod = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
            .withUserConfiguration(ProductionChecks.class);

    private ApplicationContextRunner with(String jwt, String checkin, String cors, boolean secureCookie, String dbPassword) {
        return prod.withPropertyValues("circl.jwt.secret=" + jwt, "circl.checkin.secret=" + checkin,
                "circl.cors.allowed-origins=" + cors, "circl.auth.cookie-secure=" + secureCookie,
                "spring.datasource.password=" + dbPassword);
    }

    @Test
    void startsWithRealSettings() {
        with(GOOD_JWT, GOOD_CHECKIN, "https://circl.example.com", true, "a-real-db-password")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void refusesTheDevelopmentJwtSecret() {
        with("dev-only-secret-change-me-0123456789abcdef", GOOD_CHECKIN, "https://circl.example.com", true, "pw")
                .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("CIRCL_JWT_SECRET is the development default"));
    }

    @Test
    void refusesShortOrSharedSecrets() {
        with("short", GOOD_CHECKIN, "https://circl.example.com", true, "pw")
                .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("at least 32 bytes"));
        with(GOOD_JWT, GOOD_JWT, "https://circl.example.com", true, "pw")
                .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("must be different"));
    }

    @Test
    void refusesOpenCorsInsecureCookiesAndTheComposePassword() {
        with(GOOD_JWT, GOOD_CHECKIN, "*", true, "pw")
                .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("CORS_ORIGINS"));
        with(GOOD_JWT, GOOD_CHECKIN, "http://localhost:5173", true, "pw")
                .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("CORS_ORIGINS"));
        with(GOOD_JWT, GOOD_CHECKIN, "https://circl.example.com", false, "pw")
                .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("Secure"));
        with(GOOD_JWT, GOOD_CHECKIN, "https://circl.example.com", true, "circl")
                .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("DB_PASSWORD"));
    }

    @Test
    void notCheckedOutsideTheProdProfile() {
        new ApplicationContextRunner().withUserConfiguration(ProductionChecks.class)
                .withPropertyValues("circl.jwt.secret=dev-only-secret-change-me-0123456789abcdef")
                .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(ProductionChecks.class));
    }

    @Test
    void prodConfigReadsEverySecretFromTheEnvironmentWithoutAFallback() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"));
        PropertySource<?> prodYaml = sources.get(0);
        assertThat(String.valueOf(prodYaml.getProperty("circl.jwt.secret"))).isEqualTo("${CIRCL_JWT_SECRET}");
        assertThat(String.valueOf(prodYaml.getProperty("circl.checkin.secret"))).isEqualTo("${CIRCL_CHECKIN_SECRET}");
        assertThat(String.valueOf(prodYaml.getProperty("spring.datasource.password"))).isEqualTo("${DB_PASSWORD}");
        assertThat(String.valueOf(prodYaml.getProperty("circl.cors.allowed-origins"))).isEqualTo("${CORS_ORIGINS}");
    }
}
