package com.email.declutter_ai.photos;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class GooglePhotosService {
	private final RestClient photosApi;

	public GooglePhotosService(RestClient.Builder restClientBuilder) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(15));
		this.photosApi = restClientBuilder
				.requestFactory(requestFactory)
				.baseUrl("https://photoslibrary.googleapis.com")
				.build();
	}

	public PhotosMediaPage mediaPage(String accessToken, String pageToken, int pageSize) {
		try {
			PhotosMediaResponse response = photosApi.get()
					.uri(uriBuilder -> {
						var builder = uriBuilder.path("/v1/mediaItems")
								.queryParam("pageSize", pageSize);
						if (pageToken != null && !pageToken.isBlank()) {
							builder.queryParam("pageToken", pageToken);
						}
						return builder.build();
					})
					.headers(headers -> headers.setBearerAuth(accessToken))
					.retrieve()
					.body(PhotosMediaResponse.class);
			List<PhotosMediaItem> items = response == null || response.mediaItems() == null
					? List.of() : response.mediaItems();
			return new PhotosMediaPage(items, items.size(),
					response == null ? null : response.nextPageToken(),
					pageToken != null && !pageToken.isBlank());
		}
		catch (HttpClientErrorException.Forbidden exception) {
			throw photosAccessException(exception);
		}
		catch (ResourceAccessException exception) {
			throw new PhotosAccessException(
					"Google Photos did not respond quickly enough. Try again in a moment.",
					exception);
		}
	}

	private PhotosAccessException photosAccessException(
			HttpClientErrorException exception) {
		String response = exception.getResponseBodyAsString();
		if (response.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")
				|| response.contains("insufficientPermissions")) {
			return new PhotosAccessException(
					"Google did not grant Photos access to the current login. Click Upgrade Photos access and approve Google Photos permission.",
					exception);
		}
		if (response.contains("SERVICE_DISABLED")
				|| response.contains("accessNotConfigured")) {
			return new PhotosAccessException(
					"Google Photos Library API is not enabled for this Google Cloud project.",
					exception);
		}
		if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
			return new PhotosAccessException(
					"Google denied Photos access. Confirm the OAuth consent screen includes the Google Photos app-created-data scope, then reconnect.",
					exception);
		}
		return new PhotosAccessException(
				"Google Photos access failed: " + exception.getMessage(), exception);
	}

	public record PhotosMediaPage(
			List<PhotosMediaItem> mediaItems,
			int itemCount,
			String nextPageToken,
			boolean hasPrevious) {}

	public record PhotosMediaItem(
			String id,
			String productUrl,
			String baseUrl,
			String mimeType,
			String filename,
			PhotosMediaMetadata mediaMetadata) {}

	public record PhotosMediaMetadata(
			Instant creationTime,
			String width,
			String height,
			Photo photo,
			Video video) {}

	public record Photo(String cameraMake, String cameraModel) {}
	public record Video(String status) {}
	private record PhotosMediaResponse(
			List<PhotosMediaItem> mediaItems,
			String nextPageToken) {}
}
