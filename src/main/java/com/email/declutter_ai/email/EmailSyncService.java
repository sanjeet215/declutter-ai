package com.email.declutter_ai.email;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.email.declutter_ai.gmail.GmailClient;
import com.email.declutter_ai.gmail.GmailClient.MessagePage;

@Service
public class EmailSyncService {

	private final GmailClient gmailClient;
	private final EmailMessageRepository repository;

	public EmailSyncService(GmailClient gmailClient, EmailMessageRepository repository) {
		this.gmailClient = gmailClient;
		this.repository = repository;
	}

	@Transactional
	public SyncResult syncPage(
			String accountEmail, String accessToken, int maxResults, String pageToken) {
		MessagePage page = gmailClient.listMetadata(accessToken, maxResults, pageToken);
		Instant syncTime = Instant.now();
		int created = 0;
		int updated = 0;

		for (var metadata : page.messages()) {
			var existing = repository.findByAccountEmailAndGmailMessageId(
					accountEmail, metadata.id());
			EmailMessage message;
			if (existing.isPresent()) {
				message = existing.get();
				updated++;
			}
			else {
				message = new EmailMessage(accountEmail, metadata.id());
				created++;
			}
			message.updateFrom(metadata, syncTime);
			repository.save(message);
		}

		return new SyncResult(
				page.messages().size(),
				created,
				updated,
				page.nextPageToken(),
				page.resultSizeEstimate());
	}

	@Transactional(readOnly = true)
	public List<StoredEmailMetadata> listStored(String accountEmail, int limit) {
		return repository.findByAccountEmailOrderByReceivedAtDesc(
						accountEmail, PageRequest.of(0, limit))
				.stream()
				.map(StoredEmailMetadata::from)
				.toList();
	}

	public record SyncResult(
			int processed,
			int created,
			int updated,
			String nextPageToken,
			int resultSizeEstimate) {
	}

	public record StoredEmailMetadata(
			Long id,
			String gmailMessageId,
			String gmailThreadId,
			String internetMessageId,
			String from,
			String to,
			String cc,
			String bcc,
			String subject,
			String dateHeader,
			Instant receivedAt,
			Integer sizeEstimate,
			List<String> labels,
			Instant syncedAt) {

		private static StoredEmailMetadata from(EmailMessage message) {
			return new StoredEmailMetadata(
					message.getId(),
					message.getGmailMessageId(),
					message.getGmailThreadId(),
					message.getInternetMessageId(),
					message.getSender(),
					message.getRecipientTo(),
					message.getRecipientCc(),
					message.getRecipientBcc(),
					message.getSubject(),
					message.getDateHeader(),
					message.getReceivedAt(),
					message.getSizeEstimate(),
					message.getLabels(),
					message.getSyncedAt());
		}
	}
}
