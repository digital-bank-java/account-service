package com.digitalbank.accountservice.configuration;

import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class AccountServiceSecurityConfiguration {

    private static final int MINIMUM_HMAC_KEY_BYTES = 32;

    @Bean
    @Profile("!test")
    SecurityFilterChain accountServiceSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/admin/**")
                        .hasAuthority("SCOPE_admin.internal")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean
    @Profile("test")
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Profile("!test")
    JwtDecoder accountServiceJwtDecoder(
            @Value("${auth.jwt.issuer:}") String issuer, @Value("${auth.jwt.secret:}") String encodedSecret) {
        if (!StringUtils.hasText(encodedSecret)) {
            throw new IllegalStateException("Account Service security requires auth.jwt.secret");
        }
        final byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("auth.jwt.secret must be a valid Base64 value", exception);
        }
        if (secret.length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IllegalStateException("auth.jwt.secret must decode to at least 32 bytes");
        }
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                .build();
        if (StringUtils.hasText(issuer)) {
            OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator));
        }
        return decoder;
    }
}
