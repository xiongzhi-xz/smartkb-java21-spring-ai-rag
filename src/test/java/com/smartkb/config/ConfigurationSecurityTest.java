package com.smartkb.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ConfigurationSecurityTest {

    private static final Path DEFAULT_CONFIG = Path.of("src/main/resources/application.yml");
    private static final Path HYBRID_CONFIG = Path.of("src/main/resources/application-hybrid.yml");
    private static final Path LOCAL_DEMO_CONFIG = Path.of("src/main/resources/application-local-demo.yml");

    @Test
    void shouldKeepSensitiveDefaultsAndVerboseObservabilityOutOfNonLocalProfiles() throws IOException {
        String defaultConfig = Files.readString(DEFAULT_CONFIG);
        String hybridConfig = Files.readString(HYBRID_CONFIG);

        assertThat(defaultConfig).doesNotContain("smartkb123", "local-demo-api-key", "your-api-key-here");
        assertThat(hybridConfig).doesNotContain("smartkb123", "local-demo-api-key", "your-api-key-here");
        assertThat(defaultConfig).contains(
                "probability: ${MANAGEMENT_TRACING_SAMPLING_PROBABILITY:0.1}",
                "show-details: ${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:never}",
                "com.smartkb: INFO",
                "org.springframework.jdbc: WARN"
        );
        assertThat(hybridConfig).doesNotContain("DEBUG", "TRACE", "show-details: always", "probability: 1.0");
    }

    @Test
    void shouldKeepDemoDefaultsInTheExplicitLocalDemoProfile() throws IOException {
        String localDemoConfig = Files.readString(LOCAL_DEMO_CONFIG);

        assertThat(localDemoConfig).contains(
                "password: smartkb123",
                "probability: 1.0",
                "show-details: always",
                "com.smartkb: DEBUG"
        );
    }

    @Test
    void shouldFailBeforeStartingWhenNonLocalCredentialsAreMissing() {
        MockEnvironment environment = new MockEnvironment();

        assertThatIllegalStateException().isThrownBy(() ->
                        new NonLocalCredentialEnvironmentPostProcessor()
                                .postProcessEnvironment(environment, new SpringApplication()))
                .withMessageContaining("spring.datasource.password")
                .withMessageContaining("spring.ai.openai.api-key");
    }

    @Test
    void shouldAllowNonLocalStartupOnlyWithInjectedCredentials() {
        MockEnvironment environment = credentialedEnvironment();

        new NonLocalCredentialEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication());
    }

    @Test
    void shouldSkipCredentialGuardForTheExplicitLocalDemoProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-demo");

        new NonLocalCredentialEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication());
    }

    private MockEnvironment credentialedEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("credentials", Map.of(
                "spring.datasource.password", "database-password-from-secret",
                "spring.rabbitmq.password", "rabbitmq-password-from-secret",
                "spring.ai.openai.api-key", "chat-api-key-from-secret",
                "smartkb.object-storage.access-key", "object-storage-access-key",
                "smartkb.object-storage.secret-key", "object-storage-secret-key"
        )));
        return environment;
    }
}
