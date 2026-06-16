package com.smartkb.agent.application;

import com.smartkb.agent.domain.ProjectRawContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects basic project metadata from raw intake context.
 */
class ProjectIntakeDetector {

    List<String> detectStack(ProjectRawContext raw) {
        String all = String.join("\n", raw.fileContents().values()).toLowerCase(Locale.ROOT);
        Set<String> stack = new LinkedHashSet<>();
        if (all.contains("<java.version>21</java.version>") || all.contains("java 21")) {
            stack.add("Java 21");
        }
        if (all.contains("spring-boot") || all.contains("spring boot")) {
            stack.add("Spring Boot");
        }
        if (raw.fileContents().containsKey("pom.xml")) {
            stack.add("Maven");
        }
        if (raw.fileContents().containsKey("docker-compose.yml")) {
            stack.add("Docker Compose");
        }
        if (all.contains("redis")) {
            stack.add("Redis");
        }
        if (all.contains("rocketmq")) {
            stack.add("RocketMQ");
        }
        if (all.contains("prometheus")) {
            stack.add("Prometheus");
        }
        if (all.contains("grafana")) {
            stack.add("Grafana");
        }
        return new ArrayList<>(stack);
    }

    String detectBuildTool(Map<String, String> contents) {
        if (contents.containsKey("pom.xml")) {
            return "maven";
        }
        if (contents.containsKey("build.gradle") || contents.containsKey("settings.gradle")) {
            return "gradle";
        }
        if (contents.containsKey("package.json")) {
            return "npm";
        }
        return "unknown";
    }

    List<String> detectPackageManagerEvidence(Map<String, String> contents) {
        List<String> evidence = new ArrayList<>();
        for (String file : List.of("pom.xml", "build.gradle", "settings.gradle", "package.json")) {
            if (contents.containsKey(file)) {
                evidence.add(file);
            }
        }
        return evidence;
    }

    List<String> detectTestCommands(Map<String, String> contents) {
        if (contents.containsKey("pom.xml")) {
            return List.of("mvn test", "mvn clean verify");
        }
        if (contents.containsKey("build.gradle") || contents.containsKey("settings.gradle")) {
            return List.of("gradle test", "gradle build");
        }
        if (contents.containsKey("package.json")) {
            return List.of("npm test");
        }
        return List.of();
    }

    String evidenceType(String path) {
        if ("HANDOFF.md".equals(path)) {
            return "handoff";
        }
        if ("SPEC.md".equals(path)) {
            return "spec";
        }
        if ("README.md".equals(path)) {
            return "readme";
        }
        if ("pom.xml".equals(path) || "docker-compose.yml".equals(path)) {
            return "config";
        }
        return "document";
    }
}
