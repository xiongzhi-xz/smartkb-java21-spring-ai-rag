package com.smartkb.agent.application;

import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

@Component
@ConditionalOnProperty(name = "smartkb.agent.eval-run.persistence", havingValue = "memory", matchIfMissing = true)
public class InMemoryEvalCaseRunStore implements EvalCaseRunStore {

    private final ConcurrentMap<String, EvalCaseRunResponse> runs = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> runIds = new CopyOnWriteArrayList<>();

    @Override
    public EvalCaseRunResponse save(EvalCaseRunResponse run) {
        runs.put(run.id(), run);
        runIds.addIfAbsent(run.id());
        return run;
    }

    @Override
    public Optional<EvalCaseRunResponse> findById(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public List<EvalCaseRunResponse> findAll(String projectId, String caseId, EvalCaseRunStatus status) {
        int size = runIds.size();
        return IntStream.range(0, size)
                .mapToObj(index -> runIds.get(size - index - 1))
                .map(runs::get)
                .filter(run -> run != null)
                .filter(run -> projectId == null || projectId.equals(run.projectId()))
                .filter(run -> caseId == null || caseId.equals(run.caseId()))
                .filter(run -> status == null || status == run.status())
                .toList();
    }
}
