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
    void shouldKeepWorkspaceNavigationAndPanels() throws IOException {
        String html = readIndexHtml();

        assertThat(html).contains(
                "id=\"workspaceNavChat\"",
                "id=\"workspaceNavProjectIntake\"",
                "id=\"workspaceNavAgentTask\"",
                "id=\"workspaceNavCodeContext\"",
                "id=\"workspaceNavEval\"",
                "id=\"projectIntakePanel\"",
                "id=\"agentTaskPanel\"",
                "id=\"codeContextPanel\"",
                "id=\"evalPanel\"",
                "id=\"chatContainer\"",
                "id=\"chatComposer\""
        );
        assertThat(html).contains(
                "onclick=\"openWorkspacePanel('chat')\"",
                "onclick=\"openWorkspacePanel('projectIntake')\"",
                "onclick=\"openWorkspacePanel('agentTask')\"",
                "onclick=\"openWorkspacePanel('codeContext')\"",
                "onclick=\"openWorkspacePanel('eval')\""
        );
    }

    @Test
    void shouldKeepAgentTaskAndEvalSubTabs() throws IOException {
        String html = readIndexHtml();

        assertThat(html).contains(
                "data-workspace-tab=\"agentTask\"",
                "data-workspace-tab-target=\"agentTaskCurrentTab\"",
                "data-workspace-tab-target=\"agentTaskCreateTab\"",
                "data-workspace-tab-target=\"agentTaskListTab\"",
                "id=\"agentTaskCurrentTab\"",
                "id=\"agentTaskCreateTab\"",
                "id=\"agentTaskListTab\""
        );
        assertThat(html).contains(
                "data-workspace-tab=\"eval\"",
                "data-workspace-tab-target=\"evalCreateTab\"",
                "data-workspace-tab-target=\"evalRunList\"",
                "data-workspace-tab-target=\"evalReport\"",
                "id=\"evalCreateTab\"",
                "id=\"evalRunList\"",
                "id=\"evalReport\""
        );
    }

    @Test
    void shouldKeepCoreWorkbenchFunctions() throws IOException {
        String html = readIndexHtml();

        assertThat(html).contains(
                "function openWorkspacePanel(name)",
                "function openWorkspaceSubTab(scope, targetId)",
                "function runProjectIntake()",
                "function createAgentTask()",
                "function runCodeContext()",
                "function createEvalRun()",
                "function loadEvalData()"
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
                "documentDetailOverlay",
                "documentDetailPanel",
                "documentDetailBody"
        );
    }

    private String readIndexHtml() throws IOException {
        return Files.readString(INDEX_HTML);
    }
}
