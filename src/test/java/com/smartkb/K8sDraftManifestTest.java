package com.smartkb;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class K8sDraftManifestTest {

    private static final Path DRAFT_MANIFEST = Path.of("k8s/deployment-draft.yaml");
    private static final Path K8S_README = Path.of("k8s/README.md");

    @Test
    void shouldKeepDraftManifestClearlyMarkedAsUnverified() throws IOException {
        String manifest = Files.readString(DRAFT_MANIFEST);

        assertThat(manifest).contains(
                "Draft Kubernetes manifest kept for historical design notes.",
                "Do not apply this file directly.",
                "k8s/k3s-demo.yaml"
        );
        assertThat(manifest).contains(
                "postgres-password: replace-at-deploy-time",
                "openai-api-key: replace-at-deploy-time"
        );
        assertThat(manifest).doesNotContain(
                "smartkb123",
                "sk-your-api-key-here"
        );
    }

    @Test
    void shouldKeepK8sReadmePointingToVerifiedDemoManifest() throws IOException {
        String readme = Files.readString(K8S_README);

        assertThat(readme).contains(
                "k8s/k3s-demo.yaml",
                "k8s/deployment-draft.yaml",
                "Runtime status: `k8s/k3s-demo.yaml` was verified"
        );
        assertThat(readme).contains("kubectl apply -f k8s/k3s-demo.yaml");
        assertThat(readme).doesNotContain("kubectl apply -f k8s/deployment.yaml");
    }
}
