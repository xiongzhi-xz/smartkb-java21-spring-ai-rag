package com.smartkb.infrastructure.persistence;

import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcIngestionJobRepositoryTest {

    @Test
    void shouldUseConflictSafeInsertBeforeReadingIdempotentJob() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcIngestionJobRepository repository = new JdbcIngestionJobRepository(jdbcTemplate);
        IngestionJob job = new IngestionJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "upload-key",
                IngestionJobStatus.PENDING,
                0);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Optional<IngestionJob>>>any(),
                eq("upload-key")))
                .thenReturn(Optional.of(job));

        assertThat(repository.createOrGet(job)).isEqualTo(job);

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq(job.id()),
                eq(job.documentId()),
                eq(job.idempotencyKey()),
                eq(job.status().name()),
                eq(job.retryCount()));
        assertThat(sqlCaptor.getValue()).contains("ON CONFLICT DO NOTHING");
    }
}
