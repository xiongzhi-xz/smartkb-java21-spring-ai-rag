package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.ConversationRepository;
import com.smartkb.domain.conversation.ConversationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PostgreSQL/JDBC 会话事实仓储。 */
@Repository
@RequiredArgsConstructor
public class JdbcConversationRepository implements ConversationRepository {

    private static final String ADVANCE_SEQUENCE_SQL = """
            INSERT INTO conversation (id, status, next_sequence, created_at, updated_at, last_message_at)
            VALUES (?, 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET status = 'ACTIVE',
                next_sequence = conversation.next_sequence + 1,
                updated_at = CURRENT_TIMESTAMP,
                last_message_at = CURRENT_TIMESTAMP
            RETURNING next_sequence
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void append(String conversationId, List<ConversationMessage> messages) {
        for (ConversationMessage message : messages) {
            Long sequence = jdbcTemplate.queryForObject(ADVANCE_SEQUENCE_SQL, Long.class, conversationId);
            if (sequence == null) {
                throw new IllegalStateException("会话序号生成失败: " + conversationId);
            }
            jdbcTemplate.update("""
                    INSERT INTO conversation_message
                        (id, conversation_id, sequence_no, message_type, content, created_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID(), conversationId, sequence, message.type(), message.content());
        }
    }

    @Override
    @Transactional
    public UUID appendWithMetadata(String conversationId, ConversationMessage message) {
        Long sequence = jdbcTemplate.queryForObject(ADVANCE_SEQUENCE_SQL, Long.class, conversationId);
        if (sequence == null) throw new IllegalStateException("conversation sequence generation failed: " + conversationId);
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO conversation_message
                    (id, conversation_id, sequence_no, message_type, content, citations, trace_id, created_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, CURRENT_TIMESTAMP)
                """, messageId, conversationId, sequence, message.type(), message.content(),
                message.citationsJson(), message.traceId());
        return messageId;
    }

    @Override
    public List<ConversationMessage> findRecent(String conversationId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT message_type, content
                FROM (
                    SELECT message_type, content, sequence_no
                    FROM conversation_message
                    WHERE conversation_id = ?
                    ORDER BY sequence_no DESC
                    LIMIT ?
                ) recent_messages
                ORDER BY sequence_no ASC
                """, conversationId, limit);
        return rows.stream()
                .map(row -> new ConversationMessage(
                        String.valueOf(row.get("message_type")),
                        String.valueOf(row.getOrDefault("content", ""))
                ))
                .toList();
    }

    @Override
    @Transactional
    public void clearMessages(String conversationId) {
        jdbcTemplate.update("DELETE FROM conversation_message WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("""
                UPDATE conversation
                SET status = 'CLEARED', updated_at = CURRENT_TIMESTAMP, last_message_at = NULL
                WHERE id = ?
                """, conversationId);
    }
}
