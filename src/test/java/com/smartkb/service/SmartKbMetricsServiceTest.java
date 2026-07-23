package com.smartkb.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmartKbMetricsServiceTest {
    @Test
    void shouldRecordEnterpriseDegradationAndUnavailableCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SmartKbMetricsService service = new SmartKbMetricsService(registry);

        service.recordEnterpriseRetrieval("keyword-only", 1, 12);
        service.recordEnterpriseRetrieval("unavailable", 2, 5);

        assertThat(registry.get("smartkb.retrieval.enterprise.requests").counter().count()).isEqualTo(2);
        assertThat(registry.get("smartkb.retrieval.enterprise.degraded").counter().count()).isEqualTo(1);
        assertThat(registry.get("smartkb.retrieval.enterprise.unavailable").counter().count()).isEqualTo(1);
        assertThat(registry.get("smartkb.retrieval.enterprise.duration").timer().count()).isEqualTo(2);
    }
}
