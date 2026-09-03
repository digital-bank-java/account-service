package com.digitalbank.accountservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
        properties = {
            "account.ledger.kafka.enabled=true",
            "account.ledger.kafka.auto-startup=false",
            "account.ledger.kafka.completed-topic=ledger.posting.completed.v1",
            "account.ledger.kafka.failed-topic=ledger.posting.failed.v1",
            "account.ledger.kafka.group-id=account-service-ledger-outcomes-test",
            "account.ledger.kafka.retry-attempts=3",
            "account.ledger.kafka.retry-delay-ms=10",
            "account.reservation.kafka.enabled=true",
            "account.reservation.kafka.auto-startup=false",
            "account.reservation.kafka.requested-topic=account.reservation.requested.v1",
            "account.reservation.kafka.release-requested-topic=account.reservation.release-requested.v1",
            "account.reservation.kafka.group-id=account-service-reservation-test",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.properties[security.protocol]=SSL"
        })
class AccountKafkaConfigurationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    @Qualifier("ledgerConsumerFactory")
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    @Qualifier("ledgerKafkaTemplate")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void registersBothKafkaListenerGroupsWhenBothIntegrationsAreEnabled() {
        assertThat(listenerRegistry.getListenerContainers()).hasSize(4);
    }

    @Test
    void createsProductionStringKafkaFactories() {
        assertThat(consumerFactory).isInstanceOfSatisfying(DefaultKafkaConsumerFactory.class, factory -> {
            assertThat(factory.getKeyDeserializer()).isInstanceOf(StringDeserializer.class);
            assertThat(factory.getValueDeserializer()).isInstanceOf(StringDeserializer.class);
            assertThat(factory.getConfigurationProperties())
                    .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        });
        assertThat(kafkaTemplate.getProducerFactory())
                .isInstanceOfSatisfying(DefaultKafkaProducerFactory.class, factory -> {
                    assertThat(factory.getKeySerializer()).isInstanceOf(StringSerializer.class);
                    assertThat(factory.getValueSerializer()).isInstanceOf(StringSerializer.class);
                    assertThat(factory.getConfigurationProperties())
                            .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
                });
    }
}
