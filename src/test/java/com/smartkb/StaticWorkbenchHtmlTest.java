package com.smartkb;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StaticWorkbenchHtmlTest {

    private static final Path INDEX_HTML = Path.of("src/main/resources/static/index.html");
    private static final Pattern ID_PATTERN = Pattern.compile("\\bid=\"([^\"]+)\"");

    @Test
    void shouldKeepFocusedRagWorkbench() throws IOException {
        String html = readIndexHtml();

        assertThat(html).contains(
                "id=\"workspaceNavChat\"",
                "id=\"ragModeControls\"",
                "id=\"ragEvalButton\"",
                "id=\"chatContainer\"",
                "id=\"chatComposer\"",
                "id=\"documentList\"",
                "id=\"documentDetailOverlay\""
        );
    }

    @Test
    void shouldRemoveAgentWorkbench() throws IOException {
        String html = readIndexHtml();

        assertThat(html).doesNotContain(
                "/api/agent",
                "workspaceNavProjectIntake",
                "workspaceNavAgentTask",
                "workspaceNavMemory",
                "workspaceNavCodeContext",
                "workspaceNavEval",
                "projectIntakePanel",
                "agentTaskPanel",
                "memoryPanel",
                "codeContextPanel",
                "evalPanel"
        );
    }

    @Test
    void shouldKeepRagEvaluationAndStreamingFunctions() throws IOException {
        String html = readIndexHtml();

        assertThat(html).contains(
                "function runRagEval()",
                "function renderRagEvalReport(report)",
                "function streamConversationMessage(payload, pendingMessage)",
                "function streamAdvancedMessage(payload, pendingMessage)",
                "function renderAdvancedDocumentFilter()",
                "Advanced Recall@K",
                "Advanced MRR"
        );
    }

    @Test
    void shouldKeepMobileLayoutGuard() throws IOException {
        String html = readIndexHtml();

        assertThat(html).contains(
                "@media (max-width: 768px)",
                ".app-shell",
                ".app-sidebar",
                ".app-main",
                ".chat-composer-row",
                "width: 100% !important"
        );
    }

    @Test
    void shouldKeepStaticElementIdsUnique() throws IOException {
        String html = readIndexHtml();
        Matcher matcher = ID_PATTERN.matcher(html);
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();

        while (matcher.find()) {
            String id = matcher.group(1);
            if (!seenIds.add(id)) {
                duplicateIds.add(id);
            }
        }

        assertThat(duplicateIds).isEmpty();
        assertThat(seenIds).contains(
                "workspaceTitle",
                "workspaceSubtitle",
                "documentList",
                "chatContainer",
                "documentDetailPanel",
                "documentDetailBody"
        );
    }

    private String readIndexHtml() throws IOException {
        return Files.readString(INDEX_HTML);
    }
}
