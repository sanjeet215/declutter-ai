package com.email.declutter_ai.email;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.email.declutter_ai.gmail.GmailClient;
import com.email.declutter_ai.gmail.GmailClient.MessagePage;
import com.email.declutter_ai.rules.RuleEngineService;
import com.email.declutter_ai.settings.AppParameterService;

@Service
public class EmailSyncService {

	private static final Pattern EMAIL_IN_ANGLE_BRACKETS =
			Pattern.compile("<\\s*([^<>\\s]+@[^<>\\s]+)\\s*>");

	private final GmailClient gmailClient;
	private final EmailMessageRepository repository;
	private final SpammerEmailRepository spammerEmailRepository;
	private final RuleEngineService ruleEngineService;
	private final AppParameterService appParameterService;

	public EmailSyncService(
			GmailClient gmailClient,
			EmailMessageRepository repository,
			SpammerEmailRepository spammerEmailRepository,
			RuleEngineService ruleEngineService,
			AppParameterService appParameterService) {
		this.gmailClient = gmailClient;
		this.repository = repository;
		this.spammerEmailRepository = spammerEmailRepository;
		this.ruleEngineService = ruleEngineService;
		this.appParameterService = appParameterService;
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
			var classification = ruleEngineService.classify(accountEmail, message);
			message.applyClassification(
					classification.category(),
					classification.comment(),
					classification.canDelete(),
					classification.ruleName());
			if (classification.canDelete()
					&& appParameterService.autoDeleteRecommended(accountEmail)) {
				gmailClient.trashMessage(accessToken, metadata.id());
				existing.ifPresent(repository::delete);
				continue;
			}
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

	@Transactional
	public int trashBySender(String accountEmail, String accessToken, String sender) {
		List<EmailMessage> messages = repository.findByAccountEmailAndSender(
				accountEmail, sender);
		String senderEmail = senderAddress(sender);
		int trashedMessages = gmailClient.trashMessagesFromSender(
				accessToken,
				senderEmail,
				messages.stream()
						.map(EmailMessage::getGmailMessageId)
						.toList());
		repository.deleteAll(messages);
		markSpammer(accountEmail, senderEmail, sender);
		return trashedMessages;
	}

	private void markSpammer(
			String accountEmail, String senderEmail, String senderHeader) {
		Instant markedAt = Instant.now();
		SpammerEmail spammer = spammerEmailRepository
				.findByAccountEmailAndSenderEmail(accountEmail, senderEmail)
				.orElseGet(() -> new SpammerEmail(
						accountEmail, senderEmail, senderHeader, markedAt));
		spammer.markAgain(senderHeader, markedAt);
		spammerEmailRepository.save(spammer);
	}

	@Transactional
	public void trashMessage(String accountEmail, String accessToken, Long messageId) {
		EmailMessage message = repository.findByIdAndAccountEmail(
						messageId, accountEmail)
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.NOT_FOUND,
						"Stored message was not found"));
		gmailClient.trashMessage(accessToken, message.getGmailMessageId());
		repository.delete(message);
	}

	@Transactional
	public int trashByDomain(String accountEmail, String accessToken, String domain) {
		String normalizedDomain = domain.trim().toLowerCase();
		List<EmailMessage> localMessages = repository
				.findByAccountEmailOrderByReceivedAtDesc(accountEmail)
				.stream()
				.filter(message -> domainMatches(
						senderDomain(message.getSender()), normalizedDomain))
				.toList();
		int trashedMessages = gmailClient.trashMessagesFromDomain(
				accessToken,
				normalizedDomain,
				localMessages.stream()
						.map(EmailMessage::getGmailMessageId)
						.toList());
		localMessages.stream()
				.map(EmailMessage::getSender)
				.distinct()
				.forEach(sender -> markSpammer(
						accountEmail, senderAddress(sender), sender));
		repository.deleteAll(localMessages);
		return trashedMessages;
	}

	private boolean domainMatches(String senderDomain, String selectedDomain) {
		return senderDomain != null
				&& (senderDomain.equals(selectedDomain)
						|| senderDomain.endsWith("." + selectedDomain));
	}

	private String senderAddress(String sender) {
		var matcher = EMAIL_IN_ANGLE_BRACKETS.matcher(sender);
		return matcher.find() ? matcher.group(1) : sender.trim();
	}

	public String senderDomain(String sender) {
		if (sender == null) {
			return null;
		}
		String address = senderAddress(sender);
		int at = address.lastIndexOf('@');
		return at >= 0 && at + 1 < address.length()
				? address.substring(at + 1).toLowerCase()
				: null;
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
