package com.digitalbank.accountservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;
import java.util.Map;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class LedgerKafkaConfigurationSecurityTest {

    @Test
    void rejectsEnabledPlaintextUnlessInsecureTransportIsExplicitlyAllowed() {
        assertThatThrownBy(() -> LedgerKafkaConfiguration.validateSecurityBoundary(true, "PLAINTEXT", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated Kafka transport");
        assertThatThrownBy(() -> LedgerKafkaConfiguration.validateSecurityBoundary(true, "SASL_PLAINTEXT", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authenticated Kafka transport");
    }

    @Test
    void acceptsDisabledPlaintextAndAuthenticatedTransport() {
        assertThatCode(() -> LedgerKafkaConfiguration.validateSecurityBoundary(false, "PLAINTEXT", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> LedgerKafkaConfiguration.validateSecurityBoundary(true, "SASL_SSL", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> LedgerKafkaConfiguration.validateSecurityBoundary(true, "PLAINTEXT", true))
                .doesNotThrowAnyException();
    }

    @Test
    void classifiesReservationStateConflictAsNonRetryable() {
        var properties = new LedgerKafkaProperties();
        properties.setRetryAttempts(3);
        properties.setRetryDelayMs(10);
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(
                Map.of(), new StringSerializer(), new StringSerializer());
        var handler = new LedgerKafkaConfiguration()
                .ledgerKafkaErrorHandler(new KafkaTemplate<>(producerFactory), properties);

        assertThat(handler.removeClassification(ReservationStateConflictException.class))
                .isFalse();
    }
}
