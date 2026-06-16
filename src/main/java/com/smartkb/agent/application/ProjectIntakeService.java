package com.smartkb.agent.application;

import com.smartkb.agent.domain.ProjectIntakeRequest;
import com.smartkb.agent.domain.ProjectIntakeResponse;
import com.smartkb.agent.domain.ProjectRawContext;
import com.smartkb.agent.infrastructure.filesystem.ProjectFileReader;
import com.smartkb.agent.infrastructure.filesystem.ProjectPathGuard;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic project intake service.
 */
@Service
public class ProjectIntakeService {

    private static final int DEFAULT_MAX_FILES = 200;
    private static final int DEFAULT_MAX_FILE_BYTES = 65_536;
    private static final String DEFAULT_GOAL = "接管项目并输出下一步";

    private final ProjectPathGuard pathGuard;
    private final ProjectFileReader fileReader;
    private final ProjectIntakeTextExtractor textExtractor = new ProjectIntakeTextExtractor();
    private final ProjectIntakeDetector detector = new ProjectIntakeDetector();

    public ProjectIntakeService(ProjectPathGuard pathGuard, ProjectFileReader fileReader) {
        this.pathGuard = pathGuard;
        this.fileReader = fileReader;
    }

    public ProjectIntakeResponse intake(ProjectIntakeRequest request) {
        ProjectIntakeRequest safeRequest = request == null
                ? new ProjectIntakeRequest(null, null, null, null, null)
                : request;
        Path root = pathGuard.validateProjectRoot(safeRequest.rootPath());
        boolean includeCodeTree = safeRequest.includeCodeTree() == null || safeRequest.includeCodeTree();
        int maxFiles = positiveOrDefault(safeRequest.maxFiles(), DEFAULT_MAX_FILES);
        int maxFileBytes = positiveOrDefault(safeRequest.maxFileBytes(), DEFAULT_MAX_FILE_BYTES);

        ProjectRawContext raw = fileReader.read(root, includeCodeTree, maxFiles, maxFileBytes);
        ProjectIntakeResponse.WorkingTreeSummary workingTree = buildWorkingTree(raw.gitInfo());
        ProjectIntakeResponse.IntakeSummary intake = buildIntakeSummary(raw, workingTree, safeRequest.goal());

        return new ProjectIntakeResponse(
                true,
                buildProjectSummary(root, raw),
                intake,
                buildEvidence(raw),
                buildReadLog(raw),
                raw.warnings()
        );
    }

    private ProjectIntakeResponse.ProjectSummary buildProjectSummary(Path root, ProjectRawContext raw) {
        return new ProjectIntakeResponse.ProjectSummary(
                root.getFileName().toString() + "-" + Math.abs(root.toString().hashCode()),
                root.getFileName().toString(),
                root.toString().replace('\\', '/'),
                detector.detectStack(raw),
                detector.detectBuildTool(raw.fileContents()),
                detector.detectPackageManagerEvidence(raw.fileContents()),
                detector.detectTestCommands(raw.fileContents()),
                OffsetDateTime.now().toString()
        );
    }

    private ProjectIntakeResponse.IntakeSummary buildIntakeSummary(
            ProjectRawContext raw,
            ProjectIntakeResponse.WorkingTreeSummary workingTree,
            String requestedGoal
    ) {
        String handoff = content(raw, "HANDOFF.md");
        String spec = content(raw, "SPEC.md");
        String readme = content(raw, "README.md");

        String currentGoal = textExtractor.firstNonBlank(
                textExtractor.firstParagraph(textExtractor.section(handoff, "## 当前目标")),
                textExtractor.firstParagraph(textExtractor.section(spec, "## 当前目标")),
                textExtractor.firstMeaningfulLine(readme),
                normalizeGoal(requestedGoal)
        );
        String currentStage = textExtractor.firstNonBlank(
                textExtractor.firstParagraph(textExtractor.section(handoff, "## 当前阶段")),
                textExtractor.firstParagraph(textExtractor.section(spec, "## 当前阶段")),
                "未在文档中明确记录"
        );

        List<String> completed = textExtractor.firstNonEmpty(
                textExtractor.bullets(textExtractor.section(handoff, "## 已完成")),
                textExtractor.checkedItems(spec, true)
        );
        List<String> unfinished = textExtractor.merge(
                textExtractor.numberedOrBullets(textExtractor.section(handoff, "## 下一步")),
                textExtractor.bullets(textExtractor.section(handoff, "## 未验证")),
                textExtractor.checkedItems(spec, false)
        );
        List<String> risks = textExtractor.merge(
                textExtractor.bullets(textExtractor.section(handoff, "## 风险和注意事项")),
                textExtractor.bullets(textExtractor.section(handoff, "## 风险")),
                defaultRisks()
        );
        String nextStepOnly = textExtractor.firstNonBlank(
                textExtractor.firstItem(textExtractor.numberedOrBullets(textExtractor.section(handoff, "## 下一步"))),
                textExtractor.firstItem(unfinished),
                "先补齐项目接管摘要所需证据"
        );

        return new ProjectIntakeResponse.IntakeSummary(
                currentGoal,
                currentStage,
                textExtractor.limit(completed, 20),
                textExtractor.limit(unfinished, 20),
                workingTree,
                textExtractor.limit(risks, 20),
                nextStepOnly
        );
    }

    private ProjectIntakeResponse.WorkingTreeSummary buildWorkingTree(ProjectRawContext.GitInfo gitInfo) {
        String status = gitInfo.statusShort() == null ? "" : gitInfo.statusShort();
        return new ProjectIntakeResponse.WorkingTreeSummary(
                gitInfo.gitRepository(),
                !status.isBlank(),
                status,
                gitInfo.latestCommits()
        );
    }

    private ProjectIntakeResponse.ProjectReadLog buildReadLog(ProjectRawContext raw) {
        List<String> commands = raw.gitInfo().gitRepository()
                ? List.of("git status --short", "git log --oneline -5")
                : List.of("git status --short");
        return new ProjectIntakeResponse.ProjectReadLog(
                new ArrayList<>(raw.fileContents().keySet()),
                raw.skippedFiles(),
                commands
        );
    }

    private List<ProjectIntakeResponse.ProjectEvidence> buildEvidence(ProjectRawContext raw) {
        List<ProjectIntakeResponse.ProjectEvidence> evidence = new ArrayList<>();
        for (String path : List.of("HANDOFF.md", "SPEC.md", "README.md", "AGENTS.md", "pom.xml", "docker-compose.yml")) {
            if (raw.fileContents().containsKey(path)) {
                evidence.add(new ProjectIntakeResponse.ProjectEvidence(
                        detector.evidenceType(path),
                        path,
                        null,
                        "已读取并用于项目接管摘要"
                ));
            }
        }
        evidence.add(new ProjectIntakeResponse.ProjectEvidence(
                "git",
                null,
                "git status --short",
                raw.gitInfo().gitRepository() ? "已读取工作区状态" : "目标目录不是 Git 仓库或 Git 状态读取失败"
        ));
        if (raw.gitInfo().gitRepository()) {
            evidence.add(new ProjectIntakeResponse.ProjectEvidence(
                    "git",
                    null,
                    "git log --oneline -5",
                    "已读取最近提交"
            ));
        }
        return evidence;
    }

    private String content(ProjectRawContext raw, String path) {
        return raw.fileContents().getOrDefault(path, "");
    }

    private String normalizeGoal(String goal) {
        return goal == null || goal.isBlank() ? DEFAULT_GOAL : goal.strip();
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private List<String> defaultRisks() {
        return List.of(
                "不要读取或复述 .env、token、cookie、私钥内容。",
                "不要提交运行数据、构建产物或本地缓存目录。",
                "第一版 Project Intake 只做读取和摘要，不自动修改目标项目。"
        );
    }

}
