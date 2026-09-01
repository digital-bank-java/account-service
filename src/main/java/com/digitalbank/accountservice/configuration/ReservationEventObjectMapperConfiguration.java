package com.digitalbank.accountservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
class ReservationEventObjectMapperConfiguration {

	@Bean
	ObjectMapper reservationEventObjectMapper() {
		return new ObjectMapper().registerModule(new JavaTimeModule());
	}
}
