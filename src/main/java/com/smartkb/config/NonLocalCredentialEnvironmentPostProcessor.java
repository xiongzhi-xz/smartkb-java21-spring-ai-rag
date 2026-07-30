package com.smartkb.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Fails before the application context starts when a non-local deployment lacks required credentials. */
public final class NonLocalCredentialEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String LOCAL_DEMO_PROFILE = "local-demo";
    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "spring.datasource.password",
            "spring.rabbitmq.password",
            "spring.ai.openai.api-key",
            "smartkb.object-storage.access-key",
            "smartkb.object-storage.secret-key"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (Arrays.asList(environment.getActiveProfiles()).contains(LOCAL_DEMO_PROFILE)) {
            return;
        }
        validate(environment);
    }

    static void validate(ConfigurableEnvironment environment) {
        List<String> invalidProperties = new ArrayList<>();
        for (String property : REQUIRED_PROPERTIES) {
            if (isMissingOrUnsafe(environment.getProperty(property))) {
                invalidProperties.add(property);
            }
        }
        if (!invalidProperties.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start without non-local credentials: " + String.join(", ", invalidProperties)
            );
        }
    }

    private static boolean isMissingOrUnsafe(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("smartkb123")
                || normalized.equals("local-demo-api-key")
                || normalized.contains("your-api-key")
                || normalized.contains("replace-at-deploy-time");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
