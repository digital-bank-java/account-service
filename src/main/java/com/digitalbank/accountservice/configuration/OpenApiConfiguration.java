package com.digitalbank.accountservice.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	@Bean
	OpenAPI accountServiceOpenAPI() {
		return new OpenAPI().info(new Info()
				.title("Digital Bank Account Service API")
				.description("Account lifecycle and account lookup APIs for the Digital Bank Java platform.")
				.version("1.0.0"));
	}
}
