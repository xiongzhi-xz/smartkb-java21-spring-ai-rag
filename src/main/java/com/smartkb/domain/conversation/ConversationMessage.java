package com.smartkb.domain.conversation;

/**
 * 已持久化会话消息的领域表示。
 *
 * <p>该类型与 Spring AI 的 {@code Message} 隔离，避免领域与具体模型客户端耦合。</p>
 */
public record ConversationMessage(String type, String content, String citationsJson, java.util.UUID traceId) {

    public ConversationMessage(String type, String content) {
        this(type, content, null, null);
    }

    public ConversationMessage {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("消息类型不能为空");
        }
        content = content == null ? "" : content;
    }
}
