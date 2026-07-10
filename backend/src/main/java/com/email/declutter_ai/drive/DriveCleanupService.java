package com.email.declutter_ai.drive;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.email.declutter_ai.storage.StorageAccessException;

@Service
public class DriveCleanupService {
	private final RestClient googleApi;

	public DriveCleanupService(RestClient.Builder restClientBuilder) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(15));
		this.googleApi = restClientBuilder
				.requestFactory(requestFactory)
				.baseUrl("https://www.googleapis.com")
				.build();
	}

	public UntitledFilesReport untitledFiles(String accessToken) {
		var files = new ArrayList<DriveFile>();
		String pageToken = null;
		do {
			String currentPageToken = pageToken;
			DriveFilePage page;
			try {
				page = googleApi.get()
						.uri(uriBuilder -> {
							var builder = uriBuilder.path("/drive/v3/files")
									.queryParam("pageSize", 1000)
									.queryParam("q", "trashed = false and name contains 'Untitled'")
									.queryParam("fields",
											"nextPageToken,files(id,name,mimeType,size,quotaBytesUsed,modifiedTime,webViewLink)");
							if (currentPageToken != null) {
								builder.queryParam("pageToken", currentPageToken);
							}
							return builder.build();
						})
						.headers(headers -> headers.setBearerAuth(accessToken))
						.retrieve()
						.body(DriveFilePage.class);
			}
			catch (HttpClientErrorException.Forbidden exception) {
				throw driveAccessException(exception);
			}
			if (page == null) break;
			if (page.files() != null) files.addAll(page.files());
			pageToken = page.nextPageToken();
		}
		while (pageToken != null && !pageToken.isBlank());

		long recoverableBytes = files.stream()
				.mapToLong(file -> number(file.quotaBytesUsed()) > 0
						? number(file.quotaBytesUsed()) : number(file.size()))
				.sum();
		return new UntitledFilesReport(files, files.size(), recoverableBytes);
	}

	public DriveFileListPage untitledFilesPage(
			String accessToken, String pageToken, int pageSize) {
		DriveFilePage page = listUntitledFilesPage(accessToken, pageToken, pageSize);
		List<DriveFile> files = page == null || page.files() == null
				? List.of() : page.files();
		long recoverableBytes = files.stream()
				.mapToLong(file -> number(file.quotaBytesUsed()) > 0
						? number(file.quotaBytesUsed()) : number(file.size()))
				.sum();
		return new DriveFileListPage(
				files,
				files.size(),
				recoverableBytes,
				page == null ? null : page.nextPageToken(),
				pageToken != null && !pageToken.isBlank());
	}

	public DriveFileListPage allFilesPage(
			String accessToken, String pageToken, int pageSize) {
		DriveFilePage page = listFilesPage(
				accessToken,
				pageToken,
				pageSize,
				"trashed = false");
		List<DriveFile> files = page == null || page.files() == null
				? List.of() : page.files();
		long totalBytes = files.stream()
				.mapToLong(file -> number(file.quotaBytesUsed()) > 0
						? number(file.quotaBytesUsed()) : number(file.size()))
				.sum();
		return new DriveFileListPage(
				files,
				files.size(),
				totalBytes,
				page == null ? null : page.nextPageToken(),
				pageToken != null && !pageToken.isBlank());
	}

	private DriveFilePage listUntitledFilesPage(
			String accessToken, String pageToken, int pageSize) {
		return listFilesPage(
				accessToken,
				pageToken,
				pageSize,
				"trashed = false and name contains 'Untitled'");
	}

	private DriveFilePage listFilesPage(
			String accessToken, String pageToken, int pageSize, String query) {
		try {
			return googleApi.get()
					.uri(uriBuilder -> {
						var builder = uriBuilder.path("/drive/v3/files")
								.queryParam("pageSize", pageSize)
								.queryParam("q", query)
								.queryParam("orderBy", "modifiedTime desc")
								.queryParam("fields",
										"nextPageToken,files(id,name,mimeType,size,quotaBytesUsed,modifiedTime,webViewLink)");
						if (pageToken != null && !pageToken.isBlank()) {
							builder.queryParam("pageToken", pageToken);
						}
						return builder.build();
					})
					.headers(headers -> headers.setBearerAuth(accessToken))
					.retrieve()
					.body(DriveFilePage.class);
		}
		catch (HttpClientErrorException.Forbidden exception) {
			throw driveAccessException(exception);
		}
		catch (org.springframework.web.client.ResourceAccessException exception) {
			throw new StorageAccessException(
					"Google Drive did not respond quickly enough. Try again in a moment.",
					exception);
		}
	}

	private long number(String value) {
		return value == null || value.isBlank() ? 0 : Long.parseLong(value);
	}

	private StorageAccessException driveAccessException(
			HttpClientErrorException exception) {
		String response = exception.getResponseBodyAsString();
		if (response.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")
				|| response.contains("insufficientPermissions")) {
			return new StorageAccessException(
					"Google did not grant Drive read-only access to the current login. Click Upgrade Drive access and approve the Google Drive permission.",
					exception);
		}
		if (response.contains("SERVICE_DISABLED")
				|| response.contains("accessNotConfigured")) {
			return new StorageAccessException(
					"Google Drive API is not enabled for this Google Cloud project.",
					exception);
		}
		if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
			return new StorageAccessException(
					"Google denied Drive file access. Confirm the OAuth consent screen includes the Google Drive read-only scope, then reconnect.",
					exception);
		}
		return new StorageAccessException(
				"Google Drive access failed: " + exception.getMessage(), exception);
	}

	public record UntitledFilesReport(
			List<DriveFile> files, int fileCount, long recoverableBytes) {}

	public record DriveFileListPage(
			List<DriveFile> files,
			int fileCount,
			long recoverableBytes,
			String nextPageToken,
			boolean hasPrevious) {}

	public record DriveFile(
			String id,
			String name,
			String mimeType,
			String size,
			String quotaBytesUsed,
			Instant modifiedTime,
			String webViewLink) {}

	private record DriveFilePage(String nextPageToken, List<DriveFile> files) {}
}
