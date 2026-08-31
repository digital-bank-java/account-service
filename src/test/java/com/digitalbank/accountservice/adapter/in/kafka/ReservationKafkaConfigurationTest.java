package com.digitalbank.accountservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class ReservationKafkaConfigurationTest {

	@Test
	void propagatesConfiguredSecurityProtocolToReservationFactories() {
		var environment = new MockEnvironment()
				.withProperty("spring.kafka.bootstrap-servers", "localhost:9092")
				.withProperty("spring.kafka.properties[security.protocol]", "SSL");
		var configuration = new ReservationKafkaConfiguration();

		var consumerFactory = configuration.reservationConsumerFactory(environment);
		var producerFactory = configuration.reservationProducerFactory(environment);

		assertThat(consumerFactory).isInstanceOfSatisfying(DefaultKafkaConsumerFactory.class, factory -> {
			assertThat(factory.getKeyDeserializer()).isInstanceOf(StringDeserializer.class);
			assertThat(factory.getValueDeserializer()).isInstanceOf(StringDeserializer.class);
			assertThat(factory.getConfigurationProperties())
					.containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
		});
		assertThat(producerFactory).isInstanceOfSatisfying(DefaultKafkaProducerFactory.class, factory -> {
			assertThat(factory.getKeySerializer()).isInstanceOf(StringSerializer.class);
			assertThat(factory.getValueSerializer()).isInstanceOf(StringSerializer.class);
			assertThat(factory.getConfigurationProperties())
					.containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
		});
	}
}
