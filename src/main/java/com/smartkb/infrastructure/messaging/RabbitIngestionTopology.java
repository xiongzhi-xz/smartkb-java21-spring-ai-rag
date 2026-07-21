package com.smartkb.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** 文档入库主队列与死信队列拓扑。 */
@Configuration
public class RabbitIngestionTopology {

    public static final String EXCHANGE = "smartkb.ingestion";
    public static final String QUEUE = "smartkb.ingestion.document";
    public static final String ROUTING_KEY = "document.requested";
    public static final String DEAD_LETTER_EXCHANGE = "smartkb.ingestion.dlx";
    public static final String DEAD_LETTER_QUEUE = "smartkb.ingestion.document.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "document.failed";

    @Bean
    DirectExchange ingestionExchange() { return new DirectExchange(EXCHANGE, true, false); }

    @Bean
    DirectExchange ingestionDeadLetterExchange() { return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false); }

    @Bean
    Queue ingestionQueue() {
        return new Queue(QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY
        ));
    }

    @Bean
    Queue ingestionDeadLetterQueue() { return new Queue(DEAD_LETTER_QUEUE, true); }

    @Bean
    Binding ingestionBinding() { return BindingBuilder.bind(ingestionQueue()).to(ingestionExchange()).with(ROUTING_KEY); }

    @Bean
    Binding ingestionDeadLetterBinding() { return BindingBuilder.bind(ingestionDeadLetterQueue()).to(ingestionDeadLetterExchange()).with(DEAD_LETTER_ROUTING_KEY); }

    @Bean
    MessageConverter messageConverter() { return new Jackson2JsonMessageConverter(); }
}
