package com.smartkb.agent.controller;

import com.smartkb.agent.application.CodeContextService;
import com.smartkb.agent.domain.CodeSearchRequest;
import com.smartkb.agent.domain.CodeSearchResponse;
import com.smartkb.agent.domain.CodeTreeRequest;
import com.smartkb.agent.domain.CodeTreeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent code context API.
 */
@RestController
@RequestMapping("/api/agent/code")
public class CodeContextController {

    private final CodeContextService codeContextService;

    public CodeContextController(CodeContextService codeContextService) {
        this.codeContextService = codeContextService;
    }

    @PostMapping("/tree")
    public ResponseEntity<CodeTreeResponse> tree(@RequestBody CodeTreeRequest request) {
        return ResponseEntity.ok(codeContextService.tree(request));
    }

    @PostMapping("/search")
    public ResponseEntity<CodeSearchResponse> search(@RequestBody CodeSearchRequest request) {
        return ResponseEntity.ok(codeContextService.search(request));
    }
}
