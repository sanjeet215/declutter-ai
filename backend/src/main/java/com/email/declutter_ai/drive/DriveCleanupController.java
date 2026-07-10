package com.email.declutter_ai.drive;

import java.util.Set;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.email.declutter_ai.drive.DriveCleanupService.UntitledFilesReport;
import com.email.declutter_ai.drive.DriveCleanupService.DriveFileListPage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/drive")
public class DriveCleanupController {
	private final DriveCleanupService driveCleanupService;

	public DriveCleanupController(DriveCleanupService driveCleanupService) {
		this.driveCleanupService = driveCleanupService;
	}

	@GetMapping("/untitled")
	UntitledFilesReport untitled(
			@RegisteredOAuth2AuthorizedClient("google")
					OAuth2AuthorizedClient authorizedClient) {
		return driveCleanupService.untitledFiles(
				authorizedClient.getAccessToken().getTokenValue());
	}

	@GetMapping("/untitled/page")
	DriveFileListPage untitledPage(
			@RegisteredOAuth2AuthorizedClient("google")
					OAuth2AuthorizedClient authorizedClient,
			@org.springframework.web.bind.annotation.RequestParam(required = false)
					String pageToken,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20")
					@Min(1) @Max(100) int pageSize) {
		return driveCleanupService.untitledFilesPage(
				authorizedClient.getAccessToken().getTokenValue(),
				pageToken,
				pageSize);
	}

	@GetMapping("/files/page")
	DriveFileListPage filesPage(
			@RegisteredOAuth2AuthorizedClient("google")
					OAuth2AuthorizedClient authorizedClient,
			@org.springframework.web.bind.annotation.RequestParam(required = false)
					String pageToken,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20")
					@Min(1) @Max(100) int pageSize) {
		return driveCleanupService.allFilesPage(
				authorizedClient.getAccessToken().getTokenValue(),
				pageToken,
				pageSize);
	}

	@GetMapping("/status")
	DriveAccessStatus status(
			@RegisteredOAuth2AuthorizedClient("google")
					OAuth2AuthorizedClient authorizedClient) {
		Set<String> scopes = authorizedClient.getAccessToken().getScopes();
		boolean hasDriveRead = scopes.contains(
				"https://www.googleapis.com/auth/drive.readonly");
		boolean hasDriveMetadata = scopes.contains(
				"https://www.googleapis.com/auth/drive.metadata.readonly");
		return new DriveAccessStatus(hasDriveRead || hasDriveMetadata,
				hasDriveRead, hasDriveMetadata, scopes);
	}

	record DriveAccessStatus(
			boolean hasDriveAccess,
			boolean hasDriveReadonly,
			boolean hasDriveMetadataReadonly,
			Set<String> scopes) {}
}
