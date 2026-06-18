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
                "id=\"workspaceNavMemory\"",
                "id=\"workspaceNavCodeContext\"",
                "id=\"workspaceNavEval\"",
                "id=\"projectIntakePanel\"",
                "id=\"agentTaskPanel\"",
                "id=\"memoryPanel\"",
                "id=\"codeContextPanel\"",
                "id=\"evalPanel\"",
                "id=\"chatContainer\"",
                "id=\"chatComposer\""
        );
        assertThat(html).contains(
                "onclick=\"openWorkspacePanel('chat')\"",
                "onclick=\"openWorkspacePanel('projectIntake')\"",
                "onclick=\"openWorkspacePanel('agentTask')\"",
                "onclick=\"openWorkspacePanel('memory')\"",
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
                "function renderCompactMetric(label, value, tone = 'gray')",
                "function runProjectIntake()",
                "function createAgentTask()",
                "function loadMemoryRecords()",
                "function createMemoryRecord()",
                "function importHighAuthorityMemory()",
                "function checkMemoryConflict()",
                "function renderMemorySummary(records)",
                "function getUniqueMemoryValues(records, key)",
                "function getUniqueMemoryTags(records)",
                "function runCodeContext()",
                "function renderEvalRunSummary(runs)",
                "function countEvalRunsByStatus(runs, status)",
                "function createEvalRun()",
                "function loadEvalData()"
        );
    }

    @Test
    void shouldKeepMobileWorkbenchLayoutGuard() throws IOException {
        String html = readIndexHtml();

        assertThat(html).contains(
                "@media (max-width: 768px)",
                ".hidden { display: none !important; }",
                ".app-shell",
                ".app-sidebar",
                ".app-main",
                "grid-template-columns: repeat(3, minmax(0, 1fr))",
                "width: 100% !important",
                ".document-list",
                ".sidebar-status",
                ".chat-composer-row"
        );
        assertThat(html).contains(
                "class=\"app-shell flex h-screen min-w-0 overflow-hidden\"",
                "class=\"app-sidebar flex h-screen w-80 shrink-0 flex-col border-r border-gray-200 bg-white\"",
                "class=\"app-main min-h-0 min-w-0 flex-1 flex flex-col\"",
                "class=\"workspace-header bg-white border-b px-6 py-4 flex items-center justify-between gap-4\"",
                "class=\"workspace-header-actions flex items-center justify-end gap-3 flex-wrap\""
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
                "memoryProjectId",
                "memoryList",
                "memoryConflictResult",
                "documentDetailOverlay",
                "documentDetailPanel",
                "documentDetailBody"
        );
    }

    private String readIndexHtml() throws IOException {
        return Files.readString(INDEX_HTML);
    }
}
