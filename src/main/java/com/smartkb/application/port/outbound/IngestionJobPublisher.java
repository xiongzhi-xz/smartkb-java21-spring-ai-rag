package com.smartkb.application.port.outbound;

import com.smartkb.domain.IngestionRequestedEvent;

/** 入库事件发布端口。 */
public interface IngestionJobPublisher {
    void publish(IngestionRequestedEvent event);
}
