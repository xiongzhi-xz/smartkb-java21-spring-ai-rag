package com.smartkb.agent.controller;

import com.smartkb.agent.application.AgentTaskService;
import com.smartkb.agent.domain.AgentTaskResponse;
import com.smartkb.agent.domain.CreateAgentTaskRequest;
import com.smartkb.agent.domain.TransitionAgentTaskRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {

    private final AgentTaskService agentTaskService;

    public AgentTaskController(AgentTaskService agentTaskService) {
        this.agentTaskService = agentTaskService;
    }

    @PostMapping
    public ResponseEntity<AgentTaskResponse> create(@RequestBody CreateAgentTaskRequest request) {
        return ResponseEntity.ok(agentTaskService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<AgentTaskResponse>> list() {
        return ResponseEntity.ok(agentTaskService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentTaskResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(agentTaskService.get(id));
    }

    @PostMapping("/{id}/transition")
    public ResponseEntity<AgentTaskResponse> transition(
            @PathVariable String id,
            @RequestBody TransitionAgentTaskRequest request
    ) {
        return ResponseEntity.ok(agentTaskService.transition(id, request));
    }
}
