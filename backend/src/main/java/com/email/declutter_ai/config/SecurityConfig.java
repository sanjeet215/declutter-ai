package com.email.declutter_ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			@Value("${app.frontend-url}") String frontendUrl) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/", "/error", "/api/auth/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions
						.defaultAuthenticationEntryPointFor(
								new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
								new AntPathRequestMatcher("/api/**")))
				.oauth2Login(oauth2 -> oauth2
						.defaultSuccessUrl(frontendUrl, true))
				.oauth2Client(oauth2 -> {})
				.build();
	}
}
