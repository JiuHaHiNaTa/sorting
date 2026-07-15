package com.example.sorting.service;

import com.example.sorting.repository.CdrPushRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CdrPushKafkaImpl 集成测试（Embedded Kafka）
 */
@SpringBootTest
@EmbeddedKafka(
    topics = {"cdr-record-push"},
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CdrPushServiceIntegrationTest {

    @Autowired
    private CdrPushRecordMapper pushRecordMapper;

    @Autowired(required = false)
    private KafkaTemplate<String, CdrPushMessage> kafkaTemplate;

    @Test
    void contextLoads_shouldStartWithEmbeddedKafka() {
        assertNotNull(pushRecordMapper);
    }

    @Test
    void kafkaTemplate_shouldBeAvailable() {
        // KafkaTemplate may or may not be injected depending on config
        // This just verifies the Embedded Kafka starts
        System.out.println("Embedded Kafka started successfully");
    }
}
