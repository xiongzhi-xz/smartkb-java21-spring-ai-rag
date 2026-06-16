package com.smartkb.agent.controller;

import com.smartkb.agent.application.MemoryRecordService;
import com.smartkb.agent.domain.CreateMemoryRecordRequest;
import com.smartkb.agent.domain.MemoryAuthorityLevel;
import com.smartkb.agent.domain.MemoryRecordResponse;
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
@RequestMapping("/api/agent/memories")
public class MemoryRecordController {

    private final MemoryRecordService memoryRecordService;

    public MemoryRecordController(MemoryRecordService memoryRecordService) {
        this.memoryRecordService = memoryRecordService;
    }

    @PostMapping
    public ResponseEntity<MemoryRecordResponse> create(@RequestBody CreateMemoryRecordRequest request) {
        return ResponseEntity.ok(memoryRecordService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<MemoryRecordResponse>> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) MemoryAuthorityLevel authorityLevel,
            @RequestParam(required = false) String sourceType
    ) {
        return ResponseEntity.ok(memoryRecordService.list(projectId, authorityLevel, sourceType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemoryRecordResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(memoryRecordService.get(id));
    }
}
