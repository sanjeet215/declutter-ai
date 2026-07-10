package com.email.declutter_ai.config;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ClientRegistrationRepository clientRegistrationRepository,
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
						.authorizationEndpoint(authorization -> authorization
								.authorizationRequestResolver(
										authorizationRequestResolver(clientRegistrationRepository)))
						.successHandler(authenticationSuccessHandler(frontendUrl)))
				.oauth2Client(oauth2 -> {})
				.build();
	}

	private SavedRequestAwareAuthenticationSuccessHandler authenticationSuccessHandler(
			String frontendUrl) {
		var successHandler = new SavedRequestAwareAuthenticationSuccessHandler() {
			@Override
			protected String determineTargetUrl(
					HttpServletRequest request,
					HttpServletResponse response) {
				String returnTo = (String) request.getSession()
						.getAttribute("oauth_return_to");
				if (returnTo != null && returnTo.startsWith("/")) {
					request.getSession().removeAttribute("oauth_return_to");
					return frontendUrl + returnTo;
				}
				return super.determineTargetUrl(request, response);
			}
		};
		successHandler.setDefaultTargetUrl(frontendUrl);
		successHandler.setAlwaysUseDefaultTargetUrl(false);
		return successHandler;
	}

	private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
			ClientRegistrationRepository clientRegistrationRepository) {
		var resolver = new DefaultOAuth2AuthorizationRequestResolver(
				clientRegistrationRepository, "/oauth2/authorization");
		resolver.setAuthorizationRequestCustomizer(customizer -> customizer
				.additionalParameters(parameters ->
						parameters.put("include_granted_scopes", "true")));
		return new OAuth2AuthorizationRequestResolver() {
			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
				return customize(request, resolver.resolve(request));
			}

			@Override
			public OAuth2AuthorizationRequest resolve(
					HttpServletRequest request, String clientRegistrationId) {
				return customize(request,
						resolver.resolve(request, clientRegistrationId));
			}

			private OAuth2AuthorizationRequest customize(
					HttpServletRequest request,
					OAuth2AuthorizationRequest authorizationRequest) {
				if (authorizationRequest == null
						|| !"true".equals(request.getParameter("force_consent"))) {
					rememberReturnTo(request);
					return authorizationRequest;
				}
				rememberReturnTo(request);
				var parameters = new HashMap<>(
						authorizationRequest.getAdditionalParameters());
				parameters.put("prompt", "consent");
				return OAuth2AuthorizationRequest
						.from(authorizationRequest)
						.additionalParameters(parameters)
						.build();
			}

			private void rememberReturnTo(HttpServletRequest request) {
				String returnTo = request.getParameter("return_to");
				if (returnTo != null && returnTo.startsWith("/")
						&& !returnTo.startsWith("//")) {
					request.getSession().setAttribute("oauth_return_to", returnTo);
				}
			}
		};
	}
}
