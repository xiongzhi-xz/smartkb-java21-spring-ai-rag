package com.smartkb.agent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "smartkb.agent.eval-run.persistence", havingValue = "jdbc")
public class JdbcEvalCaseRunStore implements EvalCaseRunStore {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcEvalCaseRunStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_eval_case_run (
                    id VARCHAR(64) PRIMARY KEY,
                    project_id VARCHAR(128),
                    case_id VARCHAR(64) NOT NULL,
                    title TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    score INTEGER,
                    max_score INTEGER,
                    human_interventions INTEGER,
                    tool_call_count INTEGER,
                    duration_seconds BIGINT,
                    evidence_paths TEXT NOT NULL DEFAULT '[]',
                    verification_commands TEXT NOT NULL DEFAULT '[]',
                    summary TEXT,
                    failure_reason TEXT,
                    created_at TIMESTAMPTZ NOT NULL
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_eval_case_run_project ON agent_eval_case_run(project_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_eval_case_run_case ON agent_eval_case_run(case_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_eval_case_run_status ON agent_eval_case_run(status)");
    }

    @Override
    public EvalCaseRunResponse save(EvalCaseRunResponse run) {
        jdbcTemplate.update("""
                        INSERT INTO agent_eval_case_run (
                            id, project_id, case_id, title, status, score, max_score,
                            human_interventions, tool_call_count, duration_seconds,
                            evidence_paths, verification_commands, summary, failure_reason, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                run.id(),
                run.projectId(),
                run.caseId(),
                run.title(),
                run.status().name(),
                run.score(),
                run.maxScore(),
                run.humanInterventions(),
                run.toolCallCount(),
                run.durationSeconds(),
                toJson(run.evidencePaths()),
                toJson(run.verificationCommands()),
                run.summary(),
                run.failureReason(),
                OffsetDateTime.parse(run.createdAt())
        );
        return run;
    }

    @Override
    public Optional<EvalCaseRunResponse> findById(String id) {
        List<EvalCaseRunResponse> runs = jdbcTemplate.query(
                "SELECT * FROM agent_eval_case_run WHERE id = ?",
                this::mapRun,
                id
        );
        return runs.stream().findFirst();
    }

    @Override
    public List<EvalCaseRunResponse> findAll(String projectId, String caseId, EvalCaseRunStatus status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM agent_eval_case_run WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (projectId != null) {
            sql.append(" AND project_id = ?");
            args.add(projectId);
        }
        if (caseId != null) {
            sql.append(" AND case_id = ?");
            args.add(caseId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), this::mapRun, args.toArray());
    }

    private EvalCaseRunResponse mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new EvalCaseRunResponse(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("case_id"),
                rs.getString("title"),
                EvalCaseRunStatus.valueOf(rs.getString("status")),
                getInteger(rs, "score"),
                getInteger(rs, "max_score"),
                getInteger(rs, "human_interventions"),
                getInteger(rs, "tool_call_count"),
                getLong(rs, "duration_seconds"),
                fromJson(rs.getString("evidence_paths")),
                fromJson(rs.getString("verification_commands")),
                rs.getString("summary"),
                rs.getString("failure_reason"),
                rs.getObject("created_at", OffsetDateTime.class).toString()
        );
    }

    private Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize eval run list", e);
        }
    }

    private List<String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("failed to deserialize eval run list", e);
        }
    }
}
