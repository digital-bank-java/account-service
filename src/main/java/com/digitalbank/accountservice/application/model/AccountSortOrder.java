package com.digitalbank.accountservice.application.model;

public record AccountSortOrder(
		String property,
		Direction direction) {

	public enum Direction {
		ASC,
		DESC
	}
}
