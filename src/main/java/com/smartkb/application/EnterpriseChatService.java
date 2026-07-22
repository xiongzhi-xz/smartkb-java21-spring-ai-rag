package com.smartkb.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.application.port.outbound.ConversationRepository;
import com.smartkb.application.port.outbound.RetrievalTraceRepository;
import com.smartkb.domain.*;
import com.smartkb.domain.conversation.ConversationMessage;
import com.smartkb.service.QueryRewritingService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class EnterpriseChatService {
    private final EnterpriseRetrievalService retrievalService;
    private final QueryRewritingService queryRewritingService;
    @Qualifier("openAiChatModel") private final ChatModel chatModel;
    private final ConversationRepository conversationRepository;
    private final RetrievalTraceRepository retrievalTraceRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public EnterpriseChatResult answer(String question, String conversationId, UUID knowledgeBaseId,
                                       List<UUID> documentIds, Consumer<String> stage) {
        long started = System.currentTimeMillis();
        stage.accept("rewriting");
        String rewritten = queryRewritingService.rewriteQuery(question);
        stage.accept("retrieving");
        EnterpriseRetrievalResult retrieved = retrievalService.retrieve(
                new RetrievalRequest(rewritten, knowledgeBaseId, documentIds, 8));
        List<FusedRetrievalCandidate> candidates = retrieved.candidates();
        stage.accept("generating");
        String context = candidates.stream().limit(5)
                .map(item -> item.candidate().content()).reduce("", (left, right) -> left + "\n\n" + right);
        String answer = chatModel.call(new Prompt("""
                Answer only from the supplied knowledge-base evidence. If it is insufficient, say so plainly.

                Evidence:
                %s

                Question: %s
                """.formatted(context, question))).getResult().getOutput().getContent();
        UUID traceId = UUID.randomUUID();
        List<ReferenceChunk> references = candidates.stream().limit(5).map(item -> new ReferenceChunk(
                item.candidate().documentId().toString(), item.candidate().chunkId().toString(),
                preview(item.candidate().content()))).toList();
        try {
            conversationRepository.appendWithMetadata(conversationId, new ConversationMessage("USER", question));
            UUID assistantMessageId = conversationRepository.appendWithMetadata(conversationId,
                    new ConversationMessage("ASSISTANT", answer, objectMapper.writeValueAsString(references), traceId));
            retrievalTraceRepository.save(traceId, assistantMessageId, rewritten,
                    objectMapper.writeValueAsString(candidates), retrieved.mode(), System.currentTimeMillis() - started);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("retrieval trace serialization failed", exception);
        }
        return new EnterpriseChatResult(answer, rewritten, references, retrieved.mode(), traceId,
                System.currentTimeMillis() - started);
    }

    private String preview(String content) {
        return content.length() <= 240 ? content : content.substring(0, 240) + "...";
    }
}
