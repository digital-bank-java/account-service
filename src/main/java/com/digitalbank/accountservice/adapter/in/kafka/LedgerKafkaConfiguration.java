package com.digitalbank.accountservice.adapter.in.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.digitalbank.accountservice.domain.exception.InvalidLedgerPostingEventException;
import com.digitalbank.accountservice.domain.exception.LedgerPostingOutcomeConflictException;

@Configuration
@EnableConfigurationProperties(LedgerKafkaProperties.class)
@ConditionalOnProperty(prefix = "account.ledger.kafka", name = "enabled", havingValue = "true")
public class LedgerKafkaConfiguration {

	@Bean
	LedgerPostingEventParser ledgerPostingEventParser() {
		return new LedgerPostingEventParser();
	}

	@Bean
	DefaultErrorHandler ledgerKafkaErrorHandler(
			KafkaTemplate<Object, Object> kafkaTemplate,
			LedgerKafkaProperties properties) {
		var recoverer = new DeadLetterPublishingRecoverer(
				kafkaTemplate,
				(record, exception) -> new TopicPartition(record.topic() + ".dlq", -1));
		var retries = Math.max(0, properties.getRetryAttempts() - 1L);
		var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(properties.getRetryDelayMs(), retries));
		handler.addNotRetryableExceptions(
				InvalidLedgerPostingEventException.class,
				LedgerPostingOutcomeConflictException.class);
		handler.setCommitRecovered(true);
		return handler;
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> ledgerKafkaListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory,
			DefaultErrorHandler ledgerKafkaErrorHandler) {
		var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
		factory.setConsumerFactory(consumerFactory);
		factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
		factory.setCommonErrorHandler(ledgerKafkaErrorHandler);
		return factory;
	}
}
