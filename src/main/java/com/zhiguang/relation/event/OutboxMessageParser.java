package com.zhiguang.relation.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessageParser {

    private final ObjectMapper objectMapper;

    public List<String> extractRelationPayloads(String canalMessage) {
        try {
            JsonNode root = objectMapper.readTree(canalMessage);

            if (root.path("isDdl").asBoolean(false)) {
                return List.of();
            }

            if (!"outbox".equalsIgnoreCase(root.path("table").asText())) {
                return List.of();
            }

            if (!"INSERT".equalsIgnoreCase(root.path("type").asText())) {
                return List.of();
            }

            JsonNode rows = root.path("data");

            if (!rows.isArray() || rows.isEmpty()) {
                return List.of();
            }

            List<String> payloads = new ArrayList<>();

            for (JsonNode row : rows) {
                String aggregateType = row.path("aggregate_type").asText();

                if (!OutboxTopics.AGG_FOLLOWING.equals(aggregateType)) {
                    continue;
                }

                String payload = row.path("payload").asText();

                if (!payload.isBlank()) {
                    payloads.add(payload);
                }
            }

            return payloads;
        } catch (JsonProcessingException exception) {
            log.error("Canal 消息不是合法 JSON: {}", canalMessage, exception);
            throw new IllegalArgumentException("Canal 消息格式错误", exception);
        }
    }
}