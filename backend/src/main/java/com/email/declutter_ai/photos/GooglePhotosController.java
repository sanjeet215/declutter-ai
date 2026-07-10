package com.email.declutter_ai.photos;

import java.util.Set;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.email.declutter_ai.photos.GooglePhotosService.PhotosMediaPage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/photos")
public class GooglePhotosController {
	private static final String PHOTOS_SCOPE =
			"https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata";

	private final GooglePhotosService googlePhotosService;

	public GooglePhotosController(GooglePhotosService googlePhotosService) {
		this.googlePhotosService = googlePhotosService;
	}

	@GetMapping("/status")
	PhotosAccessStatus status(
			@RegisteredOAuth2AuthorizedClient("google")
					OAuth2AuthorizedClient authorizedClient) {
		Set<String> scopes = authorizedClient.getAccessToken().getScopes();
		return new PhotosAccessStatus(scopes.contains(PHOTOS_SCOPE), scopes);
	}

	@GetMapping("/media/page")
	PhotosMediaPage mediaPage(
			@RegisteredOAuth2AuthorizedClient("google")
					OAuth2AuthorizedClient authorizedClient,
			@RequestParam(required = false) String pageToken,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
		return googlePhotosService.mediaPage(
				authorizedClient.getAccessToken().getTokenValue(),
				pageToken,
				pageSize);
	}

	record PhotosAccessStatus(boolean hasPhotosAccess, Set<String> scopes) {}
}
