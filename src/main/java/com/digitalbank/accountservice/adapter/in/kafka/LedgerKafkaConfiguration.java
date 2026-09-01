package com.digitalbank.accountservice.adapter.in.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.digitalbank.accountservice.domain.exception.InvalidLedgerPostingEventException;
import com.digitalbank.accountservice.domain.exception.LedgerPostingOutcomeConflictException;
import com.digitalbank.accountservice.domain.exception.AccountStatusConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationExpiredException;
import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;

@Configuration
@EnableKafka
@EnableConfigurationProperties(LedgerKafkaProperties.class)
@ConditionalOnProperty(prefix = "account.ledger.kafka", name = "enabled", havingValue = "true")
public class LedgerKafkaConfiguration {

	@Bean
	LedgerPostingEventParser ledgerPostingEventParser() {
		return new LedgerPostingEventParser();
	}

	@Bean
	KafkaSecurityBoundary ledgerKafkaSecurityBoundary(
			Environment environment,
			LedgerKafkaProperties properties) {
		var securityProtocol = environment.getProperty(
				"spring.kafka.properties[security.protocol]",
				environment.getProperty("spring.kafka.properties.security.protocol", "SASL_SSL"));
		validateSecurityBoundary(properties.isEnabled(), securityProtocol, properties.isAllowInsecureTransport());
		return new KafkaSecurityBoundary(securityProtocol);
	}

	@Bean
	ConsumerFactory<String, String> ledgerConsumerFactory(Environment environment, KafkaSecurityBoundary ignored) {
		var properties = commonClientProperties(environment);
		properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
				environment.getProperty("spring.kafka.consumer.enable-auto-commit", Boolean.class, false));
		properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
				environment.getProperty("spring.kafka.consumer.auto-offset-reset", "earliest"));
		properties.putAll(boundProperties(environment, "spring.kafka.consumer.properties"));
		return new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), new StringDeserializer());
	}

	@Bean
	ProducerFactory<String, String> ledgerProducerFactory(Environment environment, KafkaSecurityBoundary ignored) {
		var properties = commonClientProperties(environment);
		properties.putAll(boundProperties(environment, "spring.kafka.producer.properties"));
		return new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), new StringSerializer());
	}

	@Bean
	KafkaTemplate<String, String> ledgerKafkaTemplate(ProducerFactory<String, String> ledgerProducerFactory) {
		return new KafkaTemplate<>(ledgerProducerFactory);
	}

	@Bean
	DefaultErrorHandler ledgerKafkaErrorHandler(
			KafkaTemplate<String, String> kafkaTemplate,
			LedgerKafkaProperties properties) {
		var recoverer = new DeadLetterPublishingRecoverer(
				kafkaTemplate,
				(record, exception) -> new TopicPartition(record.topic() + ".dlq", -1));
		var retries = Math.max(0, properties.getRetryAttempts() - 1L);
		var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(properties.getRetryDelayMs(), retries));
		handler.addNotRetryableExceptions(
				InvalidLedgerPostingEventException.class,
				LedgerPostingOutcomeConflictException.class,
				AccountStatusConflictException.class,
				ReservationExpiredException.class,
				ReservationStateConflictException.class);
		handler.setCommitRecovered(true);
		return handler;
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> ledgerKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			DefaultErrorHandler ledgerKafkaErrorHandler,
			LedgerKafkaProperties properties) {
		var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
		factory.setConsumerFactory(consumerFactory);
		factory.setAutoStartup(properties.isAutoStartup());
		factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
		factory.setCommonErrorHandler(ledgerKafkaErrorHandler);
		return factory;
	}

	private static Map<String, Object> commonClientProperties(Environment environment) {
		var properties = new HashMap<String, Object>();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
				environment.getRequiredProperty("spring.kafka.bootstrap-servers"));
		properties.putAll(boundProperties(environment, "spring.kafka.properties"));
		return properties;
	}

	static void validateSecurityBoundary(
			boolean enabled,
			String securityProtocol,
			boolean allowInsecureTransport) {
		var insecureTransport = "PLAINTEXT".equalsIgnoreCase(securityProtocol)
				|| "SASL_PLAINTEXT".equalsIgnoreCase(securityProtocol);
		if (enabled && insecureTransport && !allowInsecureTransport) {
			throw new IllegalStateException(
					"Enabled ledger Kafka requires authenticated Kafka transport; explicitly allow PLAINTEXT only for SIT");
		}
	}

	private static Map<String, Object> boundProperties(Environment environment, String prefix) {
		return new HashMap<>(Binder.get(environment)
				.bind(prefix, Bindable.mapOf(String.class, Object.class))
				.orElse(Map.of()));
	}

	private record KafkaSecurityBoundary(String securityProtocol) {
	}
}
