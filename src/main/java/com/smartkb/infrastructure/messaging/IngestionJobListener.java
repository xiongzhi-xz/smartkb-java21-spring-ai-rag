package com.smartkb.infrastructure.messaging;

import com.smartkb.application.port.outbound.IngestionJobRepository;
import com.smartkb.domain.IngestionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 入库事件消费者的状态迁移入口。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionJobListener {

    private final IngestionJobRepository ingestionJobRepository;

    @RabbitListener(queues = RabbitIngestionTopology.QUEUE)
    public void consume(IngestionRequestedEvent event) {
        if (!ingestionJobRepository.markProcessing(event.jobId())) {
            log.info("跳过重复或无效入库事件: jobId={}", event.jobId());
            return;
        }
        log.info("入库任务已进入处理状态: jobId={}, documentId={}", event.jobId(), event.documentId());
    }
}
