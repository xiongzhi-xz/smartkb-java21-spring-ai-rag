package com.smartkb.agent.application;

import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;

import java.util.List;
import java.util.Optional;

public interface EvalCaseRunStore {

    EvalCaseRunResponse save(EvalCaseRunResponse run);

    Optional<EvalCaseRunResponse> findById(String id);

    List<EvalCaseRunResponse> findAll(String projectId, String caseId, EvalCaseRunStatus status);
}
