package com.digitalbank.accountservice.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReservationEventObjectMapperConfigurationTest {

    private final ObjectMapper objectMapper =
            new ReservationEventObjectMapperConfiguration().reservationEventObjectMapper();

    @Test
    void serializesInstantsAsIso8601TextForGovernedEvents() throws Exception {
        var json = objectMapper.writeValueAsString(Map.of("occurredAt", Instant.parse("2026-09-01T10:15:30Z")));

        assertThat(json).isEqualTo("{\"occurredAt\":\"2026-09-01T10:15:30Z\"}");
    }
}
