package com.email.declutter_ai.storage;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.email.declutter_ai.storage.StorageService.StorageBreakdown;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

	private final StorageService storageService;

	public StorageController(StorageService storageService) {
		this.storageService = storageService;
	}

	@GetMapping
	StorageBreakdown storage(
			@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
			@AuthenticationPrincipal OidcUser user) {
		return storageService.getBreakdown(
				user.getEmail(),
				authorizedClient.getAccessToken().getTokenValue());
	}
}
