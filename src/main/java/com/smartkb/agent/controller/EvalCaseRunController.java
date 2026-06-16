package com.smartkb.agent.controller;

import com.smartkb.agent.application.EvalCaseRunService;
import com.smartkb.agent.domain.CreateEvalCaseRunRequest;
import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/eval/runs")
public class EvalCaseRunController {

    private final EvalCaseRunService evalCaseRunService;

    public EvalCaseRunController(EvalCaseRunService evalCaseRunService) {
        this.evalCaseRunService = evalCaseRunService;
    }

    @PostMapping
    public ResponseEntity<EvalCaseRunResponse> create(@RequestBody CreateEvalCaseRunRequest request) {
        return ResponseEntity.ok(evalCaseRunService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<EvalCaseRunResponse>> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) EvalCaseRunStatus status
    ) {
        return ResponseEntity.ok(evalCaseRunService.list(projectId, caseId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvalCaseRunResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(evalCaseRunService.get(id));
    }
}
