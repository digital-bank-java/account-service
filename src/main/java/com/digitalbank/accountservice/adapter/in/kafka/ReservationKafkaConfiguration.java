package com.digitalbank.accountservice.adapter.in.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

import com.digitalbank.accountservice.domain.exception.InvalidReservationEventException;
import com.digitalbank.accountservice.domain.exception.ReservationEventConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;

@Configuration
@EnableKafka
@EnableConfigurationProperties(ReservationKafkaProperties.class)
@ConditionalOnProperty(prefix = "account.reservation.kafka", name = "enabled", havingValue = "true")
public class ReservationKafkaConfiguration {

	@Bean ReservationEventParser reservationEventParser() { return new ReservationEventParser(); }

	@Bean("reservationConsumerFactory")
	ConsumerFactory<String, String> reservationConsumerFactory(Environment environment) {
		var values = common(environment);
		values.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		values.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, environment.getProperty("spring.kafka.consumer.auto-offset-reset", "earliest"));
		return new DefaultKafkaConsumerFactory<>(values, new StringDeserializer(), new StringDeserializer());
	}

	@Bean("reservationProducerFactory")
	ProducerFactory<String, String> reservationProducerFactory(Environment environment) {
		return new DefaultKafkaProducerFactory<>(common(environment), new StringSerializer(), new StringSerializer());
	}

	@Bean("reservationKafkaTemplate")
	KafkaTemplate<String, String> reservationKafkaTemplate(@org.springframework.beans.factory.annotation.Qualifier("reservationProducerFactory") ProducerFactory<String, String> factory) {
		return new KafkaTemplate<>(factory);
	}

	@Bean("reservationKafkaErrorHandler")
	DefaultErrorHandler reservationKafkaErrorHandler(
			@org.springframework.beans.factory.annotation.Qualifier("reservationKafkaTemplate") KafkaTemplate<String, String> template,
			ReservationKafkaProperties properties) {
		var recoverer = new DeadLetterPublishingRecoverer(template, (record, exception) -> new TopicPartition(record.topic() + ".dlq", -1));
		var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(properties.getRetryDelayMs(), Math.max(0, properties.getRetryAttempts() - 1L)));
		handler.addNotRetryableExceptions(InvalidReservationEventException.class, ReservationEventConflictException.class, ReservationStateConflictException.class);
		handler.setCommitRecovered(true);
		return handler;
	}

	@Bean("reservationKafkaListenerContainerFactory")
	ConcurrentKafkaListenerContainerFactory<String, String> reservationKafkaListenerContainerFactory(
			@org.springframework.beans.factory.annotation.Qualifier("reservationConsumerFactory") ConsumerFactory<String, String> consumerFactory,
			@org.springframework.beans.factory.annotation.Qualifier("reservationKafkaErrorHandler") DefaultErrorHandler errorHandler,
			ReservationKafkaProperties properties) {
		var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
		factory.setConsumerFactory(consumerFactory); factory.setCommonErrorHandler(errorHandler); factory.setAutoStartup(properties.isAutoStartup());
		factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
		return factory;
	}

	static void validateSecurityBoundary(boolean enabled, String protocol, boolean allowInsecure) {
		var insecure = "PLAINTEXT".equalsIgnoreCase(protocol) || "SASL_PLAINTEXT".equalsIgnoreCase(protocol);
		if (enabled && insecure && !allowInsecure) throw new IllegalStateException("Enabled reservation Kafka requires authenticated transport; explicitly allow plaintext only for SIT");
	}

	@Bean
	Object reservationKafkaSecurityBoundary(Environment environment, ReservationKafkaProperties properties) {
		var protocol = environment.getProperty("spring.kafka.properties[security.protocol]", environment.getProperty("spring.kafka.properties.security.protocol", "SASL_SSL"));
		validateSecurityBoundary(properties.isEnabled(), protocol, properties.isAllowInsecureTransport());
		return new Object();
	}

	private static Map<String, Object> common(Environment environment) {
		var values = new HashMap<String, Object>(); values.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.getRequiredProperty("spring.kafka.bootstrap-servers")); return values;
	}
}
