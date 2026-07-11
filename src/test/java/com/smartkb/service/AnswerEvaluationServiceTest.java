package com.smartkb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.domain.AnswerEvaluationReport;
import com.smartkb.domain.AnswerEvaluationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AnswerEvaluationServiceTest {

    private final AnswerEvaluationService service = new AnswerEvaluationService(
            mock(ChatModel.class), new ObjectMapper());

    @Test
    void parsesJsonFromFencedOrPrefixedResponseAndClampsScores() {
        AnswerEvaluationReport report = service.parse("""
                Evaluation result:
                ```json
                {
                  "faithfulness": 1.2,
                  "answerRelevance": 0.75,
                  "contextRelevance": -0.1,
                  "faithfulnessReason": "grounded",
                  "answerRelevanceReason": "mostly direct",
                  "contextRelevanceReason": "weak context"
                }
                ```
                """);

        assertEquals(1.0, report.faithfulness());
        assertEquals(0.75, report.answerRelevance());
        assertEquals(0.0, report.contextRelevance());
        assertEquals(0.5833, report.overallScore());
        assertEquals("grounded", report.faithfulnessReason());
    }

    @Test
    void rejectsResponseWithoutJson() {
        assertThrows(IllegalStateException.class, () -> service.parse("not json"));
    }

    @Test
    void rejectsMissingQuestionAnswerOrContexts() {
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                new AnswerEvaluationRequest("", "answer", List.of("context"))));
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                new AnswerEvaluationRequest("question", "", List.of("context"))));
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(
                new AnswerEvaluationRequest("question", "answer", List.of(" "))));
    }
}
