package com.smartkb.infrastructure.messaging;

import com.smartkb.application.port.outbound.IngestionJobPublisher;
import com.smartkb.domain.IngestionRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** RabbitMQ 入库事件发布适配器。 */
@Component
@RequiredArgsConstructor
public class RabbitIngestionJobPublisher implements IngestionJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${smartkb.messaging.ingestion.exchange:smartkb.ingestion}")
    private String exchange;

    @Value("${smartkb.messaging.ingestion.routing-key:document.requested}")
    private String routingKey;

    @Override
    public void publish(IngestionRequestedEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
