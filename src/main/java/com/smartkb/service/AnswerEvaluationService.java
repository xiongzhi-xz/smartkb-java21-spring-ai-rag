package com.smartkb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.domain.AnswerEvaluationReport;
import com.smartkb.domain.AnswerEvaluationRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AnswerEvaluationService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public AnswerEvaluationService(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public AnswerEvaluationReport evaluate(AnswerEvaluationRequest request) {
        validate(request);
        String context = String.join("\n\n---\n\n", request.contexts());
        String prompt = """
                你是 RAG 答案质量评审器。根据问题、答案和检索上下文评分。
                faithfulness：答案事实是否都有上下文支持。
                answerRelevance：答案是否直接完整地回答问题。
                contextRelevance：上下文是否与问题相关并包含所需证据。
                分数范围为 0 到 1。只返回 JSON：
                {
                  "faithfulness": 0.0,
                  "answerRelevance": 0.0,
                  "contextRelevance": 0.0,
                  "faithfulnessReason": "",
                  "answerRelevanceReason": "",
                  "contextRelevanceReason": ""
                }

                问题：%s
                答案：%s
                上下文：%s
                """.formatted(request.question(), request.answer(), context);

        String content = chatModel.call(new Prompt(prompt)).getResult().getOutput().getContent();
        return parse(content);
    }

    AnswerEvaluationReport parse(String content) {
        try {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("Judge response does not contain JSON");
            }
            JsonNode root = objectMapper.readTree(content.substring(start, end + 1));
            double faithfulness = score(root, "faithfulness");
            double answerRelevance = score(root, "answerRelevance");
            double contextRelevance = score(root, "contextRelevance");
            return new AnswerEvaluationReport(
                    faithfulness,
                    answerRelevance,
                    contextRelevance,
                    round((faithfulness + answerRelevance + contextRelevance) / 3.0),
                    root.path("faithfulnessReason").asText(""),
                    root.path("answerRelevanceReason").asText(""),
                    root.path("contextRelevanceReason").asText("")
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse answer evaluation result", e);
        }
    }

    private void validate(AnswerEvaluationRequest request) {
        if (request == null || blank(request.question()) || blank(request.answer())) {
            throw new IllegalArgumentException("question and answer are required");
        }
        if (request.contexts() == null || request.contexts().stream().allMatch(this::blank)) {
            throw new IllegalArgumentException("at least one context is required");
        }
    }

    private double score(JsonNode root, String field) {
        return round(Math.max(0.0, Math.min(1.0, root.path(field).asDouble())));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
