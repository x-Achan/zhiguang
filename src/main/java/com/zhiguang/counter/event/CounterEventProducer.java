package com.zhiguang.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiguang.counter.model.CounterEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 计数事件生产者（同步路径的第三步）。
 *
 * <p><b>★ 分区键用 eid</b>：Kafka 只保证【分区内有序】。
 * 同一个实体的 +1/-1 必须进同一分区，否则消费端看到的顺序会乱。
 * 用 eid 做 key，Kafka 按 hash(eid) % 分区数 落到固定分区，同实体天然有序。</p>
 *
 * <p><b>★ 同步等待</b>：这里 {@code .get()} 阻塞直到 broker 确认（acks=all）。
 * 多花 1~3ms，换来"用户拿到成功响应时事件一定已经落盘"。
 * 如果改成异步 fire-and-forget，进程崩溃时会丢事件 ——
 * 后果是 like_record 少一行而计数已经加了，且这种"偏大"对账默认不修。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CounterEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${counter.kafka.topic:counter-events}")
    private String topic;

    /** 发送并等待 broker 确认；失败抛异常，由调用方补偿。 */
    public void publish(CounterEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("计数事件序列化失败: " + event.eventId(), e);
        }
        try {
            // kafka发消息
            kafkaTemplate.send(topic, event.eid(), payload).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("发送计数事件被中断: " + event.eventId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("发送计数事件失败: " + event.eventId(), e);
        }
    }
}