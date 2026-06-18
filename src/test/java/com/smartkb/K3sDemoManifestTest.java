package com.smartkb;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class K3sDemoManifestTest {

    private static final Path MANIFEST = Path.of("k8s/k3s-demo.yaml");

    @Test
    void shouldKeepK3sDemoManifestStructurallyDeployable() throws IOException {
        List<Map<String, Object>> docs = loadDocuments();

        assertThat(docs).hasSize(11);
        assertThat(find(docs, "Namespace", "smartkb")).isNotNull();
        assertThat(docs).noneMatch(doc -> "Secret".equals(doc.get("kind")));
        assertNamespacedResourcesStayInSmartKbNamespace(docs);

        Map<String, Object> postgres = find(docs, "Deployment", "postgres");
        assertThat(container(postgres, "postgres", "pgvector/pgvector:pg16")).isNotNull();
        assertEnv(postgres, "postgres", "POSTGRES_DB", "smartkb");
        assertEnv(postgres, "postgres", "POSTGRES_USER", "smartkb");
        assertSecretEnv(postgres, "postgres", "POSTGRES_PASSWORD", "smartkb-secrets", "postgres-password");
        assertEnv(postgres, "postgres", "PGDATA", "/var/lib/postgresql/data/pgdata");
        assertVolumeMount(postgres, "postgres", "postgres-data", "/var/lib/postgresql/data");
        assertVolumeMount(postgres, "postgres", "postgres-init", "/docker-entrypoint-initdb.d/init-db.sql");

        Map<String, Object> redis = find(docs, "Deployment", "redis");
        assertThat(container(redis, "redis", "redis:7-alpine")).isNotNull();
        assertVolumeMount(redis, "redis", "redis-data", "/data");

        Map<String, Object> app = find(docs, "Deployment", "smartkb-app");
        assertThat(container(app, "smartkb", "smartkb:local")).isNotNull();
        assertThat(container(app, "smartkb", "smartkb:local").get("imagePullPolicy")).isEqualTo("IfNotPresent");
        assertEnv(app, "smartkb", "SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres-service:5432/smartkb");
        assertEnv(app, "smartkb", "SPRING_DATASOURCE_USERNAME", "smartkb");
        assertSecretEnv(app, "smartkb", "SPRING_DATASOURCE_PASSWORD", "smartkb-secrets", "postgres-password");
        assertEnv(app, "smartkb", "SPRING_DATA_REDIS_HOST", "redis-service");
        assertEnv(app, "smartkb", "SPRING_DATA_REDIS_PORT", "6379");
        assertEnv(app, "smartkb", "SMARTKB_AGENT_EVAL_RUN_PERSISTENCE", "jdbc");
        assertEnv(app, "smartkb", "OLLAMA_BASE_URL", "http://host.k3d.internal:11434");
        assertSecretEnv(app, "smartkb", "OPENAI_API_KEY", "smartkb-secrets", "openai-api-key");
        assertHttpProbe(app, "smartkb", "readinessProbe", "/actuator/health", 8080);
        assertHttpProbe(app, "smartkb", "livenessProbe", "/actuator/health", 8080);

        assertService(docs, "postgres-service", "postgres", 5432, 5432);
        assertService(docs, "redis-service", "redis", 6379, 6379);
        assertService(docs, "smartkb-service", "smartkb-app", 80, 8080);
        assertIngressRoutesToSmartKbService(docs);
    }

    private List<Map<String, Object>> loadDocuments() throws IOException {
        Yaml yaml = new Yaml();
        Iterable<Object> loaded = yaml.loadAll(Files.readString(MANIFEST));
        return StreamSupport.stream(loaded.spliterator(), false)
                .filter(Objects::nonNull)
                .map(doc -> (Map<String, Object>) doc)
                .toList();
    }

    private void assertNamespacedResourcesStayInSmartKbNamespace(List<Map<String, Object>> docs) {
        for (Map<String, Object> doc : docs) {
            if ("Namespace".equals(doc.get("kind"))) {
                continue;
            }
            assertThat(metadata(doc).get("namespace"))
                    .as("%s/%s namespace", doc.get("kind"), metadata(doc).get("name"))
                    .isEqualTo("smartkb");
        }
    }

    private Map<String, Object> find(List<Map<String, Object>> docs, String kind, String name) {
        return docs.stream()
                .filter(doc -> kind.equals(doc.get("kind")))
                .filter(doc -> name.equals(metadata(doc).get("name")))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> metadata(Map<String, Object> doc) {
        return map(doc, "metadata");
    }

    private Map<String, Object> container(Map<String, Object> deployment, String name, String image) {
        List<Map<String, Object>> containers = containers(deployment);
        return containers.stream()
                .filter(container -> name.equals(container.get("name")))
                .filter(container -> image.equals(container.get("image")))
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> containers(Map<String, Object> deployment) {
        return list(map(map(map(deployment, "spec"), "template"), "spec"), "containers");
    }

    private void assertEnv(Map<String, Object> deployment, String containerName, String name, String value) {
        Map<String, Object> env = env(deployment, containerName, name);
        assertThat(env).as("env %s", name).isNotNull();
        assertThat(env.get("value")).isEqualTo(value);
    }

    private void assertSecretEnv(Map<String, Object> deployment, String containerName, String name, String secretName, String key) {
        Map<String, Object> env = env(deployment, containerName, name);
        assertThat(env).as("secret env %s", name).isNotNull();
        Map<String, Object> secretKeyRef = map(map(env, "valueFrom"), "secretKeyRef");
        assertThat(secretKeyRef.get("name")).isEqualTo(secretName);
        assertThat(secretKeyRef.get("key")).isEqualTo(key);
    }

    private Map<String, Object> env(Map<String, Object> deployment, String containerName, String name) {
        Map<String, Object> container = containers(deployment).stream()
                .filter(item -> containerName.equals(item.get("name")))
                .findFirst()
                .orElseThrow();
        return list(container, "env").stream()
                .filter(item -> name.equals(item.get("name")))
                .findFirst()
                .orElse(null);
    }

    private void assertVolumeMount(Map<String, Object> deployment, String containerName, String volumeName, String mountPath) {
        Map<String, Object> container = containers(deployment).stream()
                .filter(item -> containerName.equals(item.get("name")))
                .findFirst()
                .orElseThrow();
        assertThat(list(container, "volumeMounts")).anySatisfy(mount -> {
            assertThat(mount.get("name")).isEqualTo(volumeName);
            assertThat(mount.get("mountPath")).isEqualTo(mountPath);
        });
    }

    private void assertHttpProbe(Map<String, Object> deployment, String containerName, String probeName, String path, int port) {
        Map<String, Object> container = containers(deployment).stream()
                .filter(item -> containerName.equals(item.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> httpGet = map(map(container, probeName), "httpGet");
        assertThat(httpGet.get("path")).isEqualTo(path);
        assertThat(httpGet.get("port")).isEqualTo(port);
    }

    private void assertService(List<Map<String, Object>> docs, String name, String app, int port, int targetPort) {
        Map<String, Object> service = find(docs, "Service", name);
        assertThat(service).isNotNull();
        assertThat(map(map(service, "spec"), "selector").get("app")).isEqualTo(app);
        assertThat(list(map(service, "spec"), "ports")).anySatisfy(servicePort -> {
            assertThat(servicePort.get("port")).isEqualTo(port);
            assertThat(servicePort.get("targetPort")).isEqualTo(targetPort);
        });
    }

    private void assertIngressRoutesToSmartKbService(List<Map<String, Object>> docs) {
        Map<String, Object> ingress = find(docs, "Ingress", "smartkb-ingress");
        assertThat(ingress).isNotNull();
        Map<String, Object> spec = map(ingress, "spec");
        assertThat(spec.get("ingressClassName")).isEqualTo("traefik");
        List<Map<String, Object>> rules = list(spec, "rules");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).get("host")).isEqualTo("smartkb.local");
        Map<String, Object> http = map(rules.get(0), "http");
        Map<String, Object> firstPath = list(http, "paths").get(0);
        Map<String, Object> backend = map(firstPath, "backend");
        Map<String, Object> backendService = map(backend, "service");
        assertThat(backendService.get("name")).isEqualTo("smartkb-service");
        assertThat(map(backendService, "port").get("number")).isEqualTo(80);
    }

    private Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    private List<Map<String, Object>> list(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        return (List<Map<String, Object>>) value;
    }
}
