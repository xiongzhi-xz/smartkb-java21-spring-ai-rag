package com.smartkb.agent.controller;

import com.smartkb.agent.application.EvalReportService;
import com.smartkb.agent.domain.EvalReportResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/eval/report")
public class EvalReportController {

    private final EvalReportService evalReportService;

    public EvalReportController(EvalReportService evalReportService) {
        this.evalReportService = evalReportService;
    }

    @GetMapping
    public ResponseEntity<EvalReportResponse> generate(@RequestParam(required = false) String projectId) {
        return ResponseEntity.ok(evalReportService.generate(projectId));
    }
}
