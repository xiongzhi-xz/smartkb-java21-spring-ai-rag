package com.smartkb.application.port.outbound;

import com.smartkb.domain.conversation.ConversationMessage;

import java.util.List;
import java.util.Optional;

/** 可失效的会话上下文缓存端口，不能替代会话事实仓储。 */
public interface ConversationContextCache {

    Optional<List<ConversationMessage>> get(String conversationId);

    void put(String conversationId, List<ConversationMessage> messages);

    void evict(String conversationId);
}
