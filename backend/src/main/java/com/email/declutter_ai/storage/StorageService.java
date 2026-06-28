package com.email.declutter_ai.storage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.email.declutter_ai.email.EmailMessageRepository;

@Service
public class StorageService {

	private final RestClient googleApi;
	private final EmailMessageRepository emailMessageRepository;

	public StorageService(
			RestClient.Builder restClientBuilder,
			EmailMessageRepository emailMessageRepository) {
		this.googleApi = restClientBuilder
				.baseUrl("https://www.googleapis.com")
				.build();
		this.emailMessageRepository = emailMessageRepository;
	}

	@Transactional(readOnly = true)
	public StorageBreakdown getBreakdown(String accountEmail, String accessToken) {
		DriveAbout about;
		try {
			about = googleApi.get()
					.uri(uriBuilder -> uriBuilder
							.path("/drive/v3/about")
							.queryParam("fields",
									"storageQuota(limit,usage,usageInDrive,usageInDriveTrash)")
							.build())
					.headers(headers -> headers.setBearerAuth(accessToken))
					.retrieve()
					.body(DriveAbout.class);
		}
		catch (HttpClientErrorException.Forbidden exception) {
			String response = exception.getResponseBodyAsString();
			if (response.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")
					|| response.contains("insufficientPermissions")) {
				throw new StorageAccessException(
						"Google did not add Drive access to the current login. Remove Declutter AI from your Google Account connections, then connect again.",
						exception);
			}
			if (response.contains("SERVICE_DISABLED")
					|| response.contains("accessNotConfigured")) {
				throw new StorageAccessException(
						"Google Drive API is not enabled for this Google Cloud project.",
						exception);
			}
			throw new StorageAccessException(
					"Google denied storage access. Verify the Drive metadata scope and reconnect.",
					exception);
		}

		if (about == null || about.storageQuota() == null) {
			throw new IllegalStateException("Google did not return storage quota information");
		}

		StorageQuota quota = about.storageQuota();
		long used = number(quota.usage());
		long drive = number(quota.usageInDrive());
		long syncedMail = emailMessageRepository
				.sumSizeEstimateByAccountEmail(accountEmail);
		long photosAndOther = Math.max(0, used - drive - syncedMail);
		Long limit = quota.limit() == null ? null : number(quota.limit());
		Long free = limit == null ? null : Math.max(0, limit - used);

		return new StorageBreakdown(
				limit,
				used,
				free,
				drive,
				number(quota.usageInDriveTrash()),
				syncedMail,
				photosAndOther);
	}

	private long number(String value) {
		return value == null || value.isBlank() ? 0 : Long.parseLong(value);
	}

	public record StorageBreakdown(
			Long limitBytes,
			long usedBytes,
			Long freeBytes,
			long driveBytes,
			long driveTrashBytes,
			long syncedMailBytes,
			long photosAndOtherBytes) {
	}

	private record DriveAbout(StorageQuota storageQuota) {
	}

	private record StorageQuota(
			String limit,
			String usage,
			String usageInDrive,
			String usageInDriveTrash) {
	}
}
