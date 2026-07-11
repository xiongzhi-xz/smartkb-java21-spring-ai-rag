package com.smartkb.domain;

public record AnswerEvaluationReport(
        double faithfulness,
        double answerRelevance,
        double contextRelevance,
        double overallScore,
        String faithfulnessReason,
        String answerRelevanceReason,
        String contextRelevanceReason) {
}
