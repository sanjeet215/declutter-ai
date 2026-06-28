package com.email.declutter_ai.gmail;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.email.declutter_ai.email.EmailSyncService;
import com.email.declutter_ai.email.EmailSyncService.StoredEmailMetadata;
import com.email.declutter_ai.email.EmailSyncService.SyncResult;
import com.email.declutter_ai.gmail.GmailClient.MessagePage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/gmail")
public class GmailController {

	private final GmailClient gmailClient;
	private final EmailSyncService emailSyncService;

	public GmailController(GmailClient gmailClient, EmailSyncService emailSyncService) {
		this.gmailClient = gmailClient;
		this.emailSyncService = emailSyncService;
	}

	@GetMapping("/messages")
	MessagePage messages(
			@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
			@RequestParam(defaultValue = "100") @Min(1) @Max(100) int maxResults,
			@RequestParam(required = false) String pageToken) {
		return gmailClient.listMetadata(
				authorizedClient.getAccessToken().getTokenValue(),
				maxResults,
				pageToken);
	}

	@PostMapping("/sync")
	SyncResult sync(
			@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
			@AuthenticationPrincipal OidcUser user,
			@RequestParam(defaultValue = "100") @Min(1) @Max(100) int maxResults,
			@RequestParam(required = false) String pageToken) {
		return emailSyncService.syncPage(
				user.getEmail(),
				authorizedClient.getAccessToken().getTokenValue(),
				maxResults,
				pageToken);
	}

	@GetMapping("/stored")
	java.util.List<StoredEmailMetadata> stored(
			@AuthenticationPrincipal OidcUser user,
			@RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
		return emailSyncService.listStored(user.getEmail(), limit);
	}
}
