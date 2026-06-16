package com.smartkb.agent.controller;

import com.smartkb.agent.application.ProjectIntakeService;
import com.smartkb.agent.domain.ProjectIntakeRequest;
import com.smartkb.agent.domain.ProjectIntakeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent project intake API.
 */
@RestController
@RequestMapping("/api/agent/projects")
public class ProjectIntakeController {

    private final ProjectIntakeService projectIntakeService;

    public ProjectIntakeController(ProjectIntakeService projectIntakeService) {
        this.projectIntakeService = projectIntakeService;
    }

    @PostMapping("/intake")
    public ResponseEntity<ProjectIntakeResponse> intake(@RequestBody ProjectIntakeRequest request) {
        return ResponseEntity.ok(projectIntakeService.intake(request));
    }
}
