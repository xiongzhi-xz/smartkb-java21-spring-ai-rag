package com.smartkb.application.port.outbound;

import com.smartkb.domain.conversation.ConversationMessage;

import java.util.List;

/**
 * 会话事实的出站端口。
 *
 * <p>应用层通过此端口读取和写入会话，不依赖 PostgreSQL、JDBC 或 Redis 的实现细节。</p>
 */
public interface ConversationRepository {

    void append(String conversationId, List<ConversationMessage> messages);

    java.util.UUID appendWithMetadata(String conversationId, ConversationMessage message);

    List<ConversationMessage> findRecent(String conversationId, int limit);

    void clearMessages(String conversationId);
}
