package com.digitalbank.accountservice.domain.model;

import java.util.UUID;

public record CustomerId(UUID value) {

	public CustomerId {
		if (value == null) {
			throw new IllegalArgumentException("Customer id is required");
		}
	}

	public static CustomerId newId() {
		return new CustomerId(UUID.randomUUID());
	}
}
