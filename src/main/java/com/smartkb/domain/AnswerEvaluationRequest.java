package com.smartkb.domain;

import java.util.List;

public record AnswerEvaluationRequest(
        String question,
        String answer,
        List<String> contexts) {
}
