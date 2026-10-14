package com.zhiguang.relation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiguang.relation.process.RelationEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RelationEventConsumer {

    private final OutboxMessageParser parser;
    private final RelationEventProcessor processor;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${relation.outbox.topic:canal-outbox}", groupId = "${relation.outbox.consumer-group:relation-projection}")
    public void onMessages(List<String> messages, Acknowledgment acknowledgment) {
        try {
            for (String message : messages) {
                List<String> payloads = parser.extractRelationPayloads(message);

                for (String payload : payloads) {
                    RelationEvent event = objectMapper.readValue(
                            payload,
                            RelationEvent.class
                    );
                    processor.process(event);
                }
            }
            acknowledgment.acknowledge();
        } catch (Exception exception) {
            log.error("关系事件消费失败，将由 Kafka 重试", exception);
            throw new IllegalStateException("关系事件消费失败", exception);
        }
    }
}