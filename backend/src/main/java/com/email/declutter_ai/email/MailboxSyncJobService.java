package com.email.declutter_ai.email;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
public class MailboxSyncJobService {

	private static final int PAGE_SIZE = 100;

	private final EmailSyncService emailSyncService;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final Map<String, SyncStatus> statuses = new ConcurrentHashMap<>();

	public MailboxSyncJobService(EmailSyncService emailSyncService) {
		this.emailSyncService = emailSyncService;
	}

	public SyncStatus start(String accountEmail, String accessToken) {
		SyncStatus current = statuses.get(accountEmail);
		if (current != null && current.state() == SyncState.RUNNING) {
			return current;
		}

		SyncStatus initial = new SyncStatus(
				SyncState.RUNNING, 0, 0, 0, 0, null);
		statuses.put(accountEmail, initial);
		executor.submit(() -> runSync(accountEmail, accessToken));
		return initial;
	}

	public SyncStatus status(String accountEmail) {
		return statuses.getOrDefault(accountEmail,
				new SyncStatus(SyncState.IDLE, 0, 0, 0, 0, null));
	}

	private void runSync(String accountEmail, String accessToken) {
		String pageToken = null;
		int processed = 0;
		int created = 0;
		int updated = 0;
		int total = 0;

		try {
			do {
				var page = emailSyncService.syncPage(
						accountEmail, accessToken, PAGE_SIZE, pageToken);
				processed += page.processed();
				created += page.created();
				updated += page.updated();
				total = page.resultSizeEstimate();
				pageToken = page.nextPageToken();
				statuses.put(accountEmail, new SyncStatus(
						SyncState.RUNNING, processed, created, updated, total, null));
			}
			while (pageToken != null && !pageToken.isBlank());

			statuses.put(accountEmail, new SyncStatus(
					SyncState.COMPLETED, processed, created, updated, total, null));
		}
		catch (Exception exception) {
			statuses.put(accountEmail, new SyncStatus(
					SyncState.FAILED, processed, created, updated, total,
					exception.getMessage() == null
							? "Mailbox sync failed"
							: exception.getMessage()));
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	public enum SyncState {
		IDLE, RUNNING, COMPLETED, FAILED
	}

	public record SyncStatus(
			SyncState state,
			int processed,
			int created,
			int updated,
			int total,
			String error) {
	}
}
