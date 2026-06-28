package com.email.declutter_ai.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	@GetMapping("/status")
	AuthStatus status(@AuthenticationPrincipal OidcUser user) {
		if (user == null) {
			return new AuthStatus(false, null, null, null);
		}
		return new AuthStatus(true, user.getEmail(), user.getFullName(), user.getPicture());
	}

	@GetMapping("/csrf")
	CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
	}

	record AuthStatus(boolean connected, String email, String name, String picture) {
	}

	record CsrfResponse(String headerName, String parameterName, String token) {
	}
}
