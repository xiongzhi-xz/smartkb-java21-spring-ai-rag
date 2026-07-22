package com.smartkb.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorDocumentIndexCleanerTest {

    @Test
    void shouldDeleteVectorChunksByEnterpriseDocumentId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgVectorDocumentIndexCleaner cleaner = new PgVectorDocumentIndexCleaner(jdbcTemplate);
        UUID documentId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(documentId.toString()))).thenReturn(4);

        assertThat(cleaner.deleteByDocumentId(documentId)).isEqualTo(4);

        var sql = forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(documentId.toString()));
        assertThat(sql.getValue()).contains("metadata->>'documentId' = ?");
    }
}
