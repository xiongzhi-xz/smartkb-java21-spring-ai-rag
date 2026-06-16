package com.smartkb.agent.application;

import com.smartkb.agent.domain.ProjectRawContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectIntakeDetectorTest {

    private final ProjectIntakeDetector detector = new ProjectIntakeDetector();

    @Test
    void shouldDetectJavaSpringAndInfraStackFromProjectFiles() {
        ProjectRawContext raw = rawContext(Map.of(
                "pom.xml", """
                        <project>
                          <properties>
                            <java.version>21</java.version>
                          </properties>
                          <dependencies>
                            <dependency>
                              <artifactId>spring-boot-starter-web</artifactId>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                "docker-compose.yml", """
                        services:
                          redis:
                            image: redis:7
                          rocketmq:
                            image: apache/rocketmq
                          prometheus:
                            image: prom/prometheus
                          grafana:
                            image: grafana/grafana
                        """
        ));

        List<String> stack = detector.detectStack(raw);

        assertEquals(
                List.of("Java 21", "Spring Boot", "Maven", "Docker Compose", "Redis", "RocketMQ", "Prometheus", "Grafana"),
                stack
        );
    }

    @Test
    void shouldDetectBuildToolAndTestCommandsByManifestPriority() {
        assertEquals("maven", detector.detectBuildTool(Map.of("pom.xml", "")));
        assertEquals("gradle", detector.detectBuildTool(Map.of("settings.gradle", "")));
        assertEquals("npm", detector.detectBuildTool(Map.of("package.json", "")));
        assertEquals("unknown", detector.detectBuildTool(Map.of("README.md", "")));

        assertEquals(List.of("mvn test", "mvn clean verify"), detector.detectTestCommands(Map.of("pom.xml", "")));
        assertEquals(List.of("gradle test", "gradle build"), detector.detectTestCommands(Map.of("build.gradle", "")));
        assertEquals(List.of("npm test"), detector.detectTestCommands(Map.of("package.json", "")));
        assertEquals(List.of(), detector.detectTestCommands(Map.of("README.md", "")));
    }

    @Test
    void shouldCollectPackageManagerEvidenceInStableOrder() {
        List<String> evidence = detector.detectPackageManagerEvidence(Map.of(
                "package.json", "{}",
                "pom.xml", "<project />",
                "settings.gradle", "",
                "README.md", ""
        ));

        assertEquals(List.of("pom.xml", "settings.gradle", "package.json"), evidence);
    }

    @Test
    void shouldClassifyEvidenceTypes() {
        assertEquals("handoff", detector.evidenceType("HANDOFF.md"));
        assertEquals("spec", detector.evidenceType("SPEC.md"));
        assertEquals("readme", detector.evidenceType("README.md"));
        assertEquals("config", detector.evidenceType("pom.xml"));
        assertEquals("config", detector.evidenceType("docker-compose.yml"));
        assertEquals("document", detector.evidenceType("docs/notes.md"));
    }

    @Test
    void shouldReturnEmptyStackWhenNoKnownSignalsExist() {
        assertTrue(detector.detectStack(rawContext(Map.of("README.md", "plain docs"))).isEmpty());
    }

    private ProjectRawContext rawContext(Map<String, String> contents) {
        return new ProjectRawContext(
                Path.of("."),
                contents,
                List.of(),
                List.of(),
                List.of(),
                new ProjectRawContext.GitInfo(false, "", List.of(), List.of())
        );
    }
}
