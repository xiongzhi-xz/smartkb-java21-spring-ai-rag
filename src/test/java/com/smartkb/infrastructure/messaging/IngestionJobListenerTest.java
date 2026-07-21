package com.smartkb.infrastructure.messaging;

import com.smartkb.application.port.outbound.IngestionJobRepository;
import com.smartkb.domain.IngestionRequestedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionJobListenerTest {

    @Test
    void shouldAttemptAtomicProcessingTransitionForEveryEvent() {
        IngestionJobRepository repository = mock(IngestionJobRepository.class);
        UUID jobId = UUID.randomUUID();
        when(repository.markProcessing(jobId)).thenReturn(true);

        new IngestionJobListener(repository)
                .consume(new IngestionRequestedEvent(jobId, UUID.randomUUID(), "upload-1"));

        verify(repository).markProcessing(jobId);
    }
}
