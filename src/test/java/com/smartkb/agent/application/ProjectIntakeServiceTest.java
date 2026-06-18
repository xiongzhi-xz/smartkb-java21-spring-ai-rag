package com.smartkb.agent.application;

import com.smartkb.agent.domain.ProjectIntakeException;
import com.smartkb.agent.domain.ProjectIntakeRequest;
import com.smartkb.agent.domain.ProjectIntakeResponse;
import com.smartkb.agent.infrastructure.filesystem.ProjectFileReader;
import com.smartkb.agent.infrastructure.filesystem.ProjectPathGuard;
import com.smartkb.agent.infrastructure.git.GitCommandReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectIntakeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldBuildProjectIntakeFromWhitelistedDocs() throws IOException {
        write("HANDOFF.md", """
                # HANDOFF

                ## 当前目标

                把 Demo 项目收口为可本地运行、可压测、可面试讲解的 Java 项目。

                ## 当前阶段

                Docker Compose 已验证，下一步是 k6 压测。

                ## 已完成

                - Java 21 + Spring Boot 项目骨架。
                - Redis Lua 防超卖。

                ## 下一步

                1. 用 k6 跑三种库存策略压测。
                2. 补稳定性压测记录。

                ## 未验证

                - Virtual Threads 对比报告。

                ## 风险和注意事项

                - 不要提交运行数据目录。
                """);
        write("SPEC.md", """
                # SPEC

                - [x] RocketMQ 异步下单
                - [ ] Seata 示例
                """);
        write("README.md", "# Demo\n\nJava 21 Spring Boot demo.");
        write("pom.xml", """
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
                """);
        write("docker-compose.yml", "services:\n  redis:\n    image: redis:7\n");
        write("src/main/java/com/example/App.java", "class App {}\n");
        write("docs/.env", "SECRET=value\n");

        ProjectIntakeResponse response = service().intake(new ProjectIntakeRequest(
                tempDir.toString(),
                "接管 Demo",
                true,
                100,
                65_536
        ));

        assertTrue(response.success());
        assertEquals(tempDir.getFileName().toString(), response.project().name());
        assertTrue(response.project().detectedStack().contains("Java 21"));
        assertTrue(response.project().detectedStack().contains("Spring Boot"));
        assertEquals("maven", response.project().buildTool());
        assertTrue(response.project().testCommands().contains("mvn test"));

        assertTrue(response.intake().currentGoal().contains("Demo 项目"));
        assertTrue(response.intake().currentStage().contains("Docker Compose 已验证"));
        assertTrue(response.intake().completed().contains("Redis Lua 防超卖。"));
        assertTrue(response.intake().unfinished().contains("用 k6 跑三种库存策略压测。"));
        assertTrue(response.intake().unfinished().contains("Seata 示例"));
        assertEquals("用 k6 跑三种库存策略压测。", response.intake().nextStepOnly());
        assertTrue(response.intake().takeoverBrief().contains("当前目标："));
        assertTrue(response.intake().takeoverBrief().contains("下一步只做：用 k6 跑三种库存策略压测。"));
        assertTrue(response.intake().stackEvidence().stream()
                .anyMatch(item -> item.startsWith("Java 21：") && item.contains("pom.xml")));
        assertTrue(response.intake().stackEvidence().stream()
                .anyMatch(item -> item.startsWith("Redis：") && item.contains("docker-compose.yml")));
        assertTrue(response.intake().runnableCommands().contains("mvn test"));
        assertTrue(response.intake().runnableCommands().contains("docker compose up -d"));
        assertTrue(response.intake().verificationGaps().contains("Virtual Threads 对比报告。"));
        assertTrue(response.intake().verificationGaps().contains("Seata 示例"));
        assertTrue(response.intake().risks().contains("不要提交运行数据目录。"));

        assertFalse(response.intake().workingTree().isGitRepository());
        assertTrue(response.readLog().readFiles().contains("HANDOFF.md"));
        assertTrue(response.readLog().readFiles().contains("pom.xml"));
        assertTrue(response.readLog().skippedFiles().stream()
                .anyMatch(file -> "docs/.env".equals(file.path()) && "sensitive file".equals(file.reason())));
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("git status --short")));
    }

    @Test
    void shouldPreferLatestSnapshotFromHandoff() throws IOException {
        write("HANDOFF.md", """
                # HANDOFF

                ## Latest Snapshot - 2026-06-18

                Current goal:
                - Keep SmartKB stable while continuing Agent platform polish.

                Current stage:
                - Docker Compose, Eval Run persistence, and K3d demo verification are complete.

                Recently completed:
                - Added K8s draft guard coverage.

                Next step only:
                - Continue SmartKB v2 Agent platform polish in a small slice.

                ## 当前目标

                Stale historical goal.

                ## 当前阶段

                Stale historical stage.

                ## 下一步
                1. Stale historical next step.
                """);
        write("SPEC.md", """
                # SPEC

                - [x] Completed SPEC item
                - [ ] Pending SPEC item
                """);
        write("README.md", "# Demo\n\nJava 21 Spring Boot demo.");
        write("pom.xml", """
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
                """);

        ProjectIntakeResponse response = service().intake(new ProjectIntakeRequest(
                tempDir.toString(),
                "Take over SmartKB",
                true,
                100,
                65_536
        ));

        assertTrue(response.intake().currentGoal().contains("Keep SmartKB stable"));
        assertTrue(response.intake().currentStage().contains("K3d demo verification are complete"));
        assertTrue(response.intake().completed().contains("Added K8s draft guard coverage."));
        assertTrue(response.intake().unfinished().contains("Continue SmartKB v2 Agent platform polish in a small slice."));
        assertEquals("Continue SmartKB v2 Agent platform polish in a small slice.", response.intake().nextStepOnly());
        assertFalse(response.intake().currentGoal().contains("Stale historical goal"));
        assertFalse(response.intake().currentStage().contains("Stale historical stage"));
    }

    @Test
    void shouldRejectMissingProjectPath() {
        ProjectIntakeException exception = assertThrows(
                ProjectIntakeException.class,
                () -> service().intake(new ProjectIntakeRequest(
                        tempDir.resolve("missing").toString(),
                        null,
                        null,
                        null,
                        null
                ))
        );

        assertEquals("PROJECT_PATH_NOT_FOUND", exception.code());
    }

    @Test
    void shouldRejectBlankRootPath() {
        ProjectIntakeException exception = assertThrows(
                ProjectIntakeException.class,
                () -> service().intake(new ProjectIntakeRequest("", null, null, null, null))
        );

        assertEquals("PROJECT_PATH_REQUIRED", exception.code());
    }

    private ProjectIntakeService service() {
        ProjectPathGuard pathGuard = new ProjectPathGuard();
        GitCommandReader gitCommandReader = new GitCommandReader();
        ProjectFileReader fileReader = new ProjectFileReader(pathGuard, gitCommandReader);
        return new ProjectIntakeService(pathGuard, fileReader);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
