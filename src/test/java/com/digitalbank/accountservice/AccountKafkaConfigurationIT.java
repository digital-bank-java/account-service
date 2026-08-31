package com.digitalbank.accountservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {
		"account.ledger.kafka.enabled=true",
		"account.ledger.kafka.auto-startup=false",
		"account.ledger.kafka.completed-topic=ledger.posting.completed.v1",
		"account.ledger.kafka.failed-topic=ledger.posting.failed.v1",
		"account.ledger.kafka.group-id=account-service-ledger-outcomes-test",
		"account.ledger.kafka.retry-attempts=3",
		"account.ledger.kafka.retry-delay-ms=10",
		"spring.kafka.bootstrap-servers=localhost:9092"
})
@ContextConfiguration(classes = AccountKafkaConfigurationIT.KafkaTestConfiguration.class)
class AccountKafkaConfigurationIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	private KafkaListenerEndpointRegistry listenerRegistry;

	@Test
	void registersLedgerKafkaListenersWhenIntegrationIsEnabled() {
		assertThat(listenerRegistry.getListenerContainers()).hasSize(2);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class KafkaTestConfiguration {

		@Bean
		ConsumerFactory<String, String> consumerFactory() {
			return new DefaultKafkaConsumerFactory<>(consumerProperties());
		}

		@Bean
		ProducerFactory<Object, Object> producerFactory() {
			return new DefaultKafkaProducerFactory<>(producerProperties());
		}

		@Bean
		KafkaTemplate<Object, Object> kafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
			return new KafkaTemplate<>(producerFactory);
		}

		private static Map<String, Object> consumerProperties() {
			var properties = new HashMap<String, Object>();
			properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
			properties.put(ConsumerConfig.GROUP_ID_CONFIG, "account-service-ledger-outcomes-test");
			properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
			properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
			return properties;
		}

		private static Map<String, Object> producerProperties() {
			var properties = new HashMap<String, Object>();
			properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
			properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			return properties;
		}
	}
}
