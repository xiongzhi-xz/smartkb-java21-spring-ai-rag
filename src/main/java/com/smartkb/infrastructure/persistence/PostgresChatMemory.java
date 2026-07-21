package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.ConversationRepository;
import com.smartkb.domain.conversation.ConversationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * PostgreSQL 持久化会话记忆。
 *
 * <p>会话与消息是需要审计和服务重启后恢复的业务事实，因此写入 PostgreSQL。Redis 只在后续
 * 作为可失效的摘要和热点上下文缓存使用。</p>
 *
 * <p>写入前原子递增 {@code conversation.next_sequence}，避免多实例并发写入同一会话时出现
 * 重复序号或不稳定顺序。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class PostgresChatMemory implements ChatMemory {

    private final ConversationRepository conversationRepository;

    @Override
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.isBlank() || messages == null || messages.isEmpty()) {
            return;
        }

        List<ConversationMessage> persistentMessages = messages.stream()
                .filter(java.util.Objects::nonNull)
                .map(message -> new ConversationMessage(messageType(message), message.getContent()))
                .toList();
        conversationRepository.append(conversationId, persistentMessages);
        log.debug("已持久化会话消息: conversationId={}, count={}", conversationId, messages.size());
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (conversationId == null || conversationId.isBlank() || lastN <= 0) {
            return Collections.emptyList();
        }
        return conversationRepository.findRecent(conversationId, lastN).stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        conversationRepository.clearMessages(conversationId);
        log.info("已清除持久化会话消息: conversationId={}", conversationId);
    }

    private Message toMessage(ConversationMessage message) {
        return switch (message.type()) {
            case "ASSISTANT" -> new AssistantMessage(message.content());
            case "SYSTEM" -> new SystemMessage(message.content());
            default -> new UserMessage(message.content());
        };
    }

    private String messageType(Message message) {
        if (message instanceof AssistantMessage) {
            return "ASSISTANT";
        }
        if (message instanceof SystemMessage) {
            return "SYSTEM";
        }
        return "USER";
    }
}
