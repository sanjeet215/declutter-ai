package com.email.declutter_ai.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.email.declutter_ai.email.EmailMessage;
import com.email.declutter_ai.email.EmailMessageRepository;
import com.email.declutter_ai.email.EmailSyncService;
import com.email.declutter_ai.email.DomainMessageCount;
import com.email.declutter_ai.email.SenderMessageCount;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/emails")
public class EmailMessageController {

	private final EmailMessageRepository emailMessageRepository;
	private final EmailSyncService emailSyncService;

	public EmailMessageController(
			EmailMessageRepository emailMessageRepository,
			EmailSyncService emailSyncService) {
		this.emailMessageRepository = emailMessageRepository;
		this.emailSyncService = emailSyncService;
	}

	@GetMapping("/aggregate")
	Map<String, Object> aggregate(
			@AuthenticationPrincipal OidcUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
		
		String accountEmail = user.getEmail();
		List<EmailMessage> emails;
		
		if (startDate != null && endDate != null) {
			emails = emailMessageRepository.findByAccountEmailAndReceivedAtBetweenOrderByReceivedAtDesc(
					accountEmail, startDate, endDate);
		} else {
			emails = emailMessageRepository.findByAccountEmailOrderByReceivedAtDesc(accountEmail);
		}

		Map<String, Object> aggregation = new HashMap<>();
		aggregation.put("totalEmails", emails.size());
		aggregation.put("totalSize", emails.stream().mapToInt(e -> e.getSizeEstimate() != null ? e.getSizeEstimate() : 0).sum());
		aggregation.put("uniqueSenders", emails.stream().map(EmailMessage::getSender).filter(s -> s != null).distinct().count());
		aggregation.put("emailsByLabel", aggregateByLabel(emails));
		aggregation.put("emailsBySender", aggregateBySender(emails));
		
		return aggregation;
	}

	@GetMapping("/stats")
	Map<String, Object> stats(@AuthenticationPrincipal OidcUser user) {
		String accountEmail = user.getEmail();
		List<EmailMessage> emails = emailMessageRepository.findByAccountEmailOrderByReceivedAtDesc(accountEmail);

		Map<String, Object> stats = new HashMap<>();
		stats.put("totalEmails", emails.size());
		stats.put("totalSizeBytes", emails.stream().mapToInt(e -> e.getSizeEstimate() != null ? e.getSizeEstimate() : 0).sum());
		stats.put("uniqueSenders", emails.stream().map(EmailMessage::getSender).filter(s -> s != null).distinct().count());
		stats.put("earliestEmail", emails.stream().map(EmailMessage::getReceivedAt).filter(d -> d != null).min(Instant::compareTo).orElse(null));
		stats.put("latestEmail", emails.stream().map(EmailMessage::getReceivedAt).filter(d -> d != null).max(Instant::compareTo).orElse(null));
		
		return stats;
	}

	@GetMapping("/senders")
	SenderPage senders(
			@AuthenticationPrincipal OidcUser user,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "15") @Min(1) @Max(100) int size) {
		var result = emailMessageRepository.findSenderMessageCountsPage(
				user.getEmail(), PageRequest.of(page, size));
		return new SenderPage(
				result.getContent(),
				result.getNumber(),
				result.getTotalPages(),
				result.getTotalElements(),
				result.hasNext(),
				result.hasPrevious());
	}

	@GetMapping("/sender-details")
	SenderDetails senderDetails(
			@AuthenticationPrincipal OidcUser user,
			@RequestParam String sender,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
		var result = emailMessageRepository
				.findByAccountEmailAndSenderOrderByReceivedAtDesc(
						user.getEmail(), sender, PageRequest.of(page, size));
		return new SenderDetails(
				sender,
				result.getTotalElements(),
				result.getContent().stream().map(SenderMessageDetail::from).toList(),
				result.getNumber(),
				result.getTotalPages(),
				result.hasNext(),
				result.hasPrevious());
	}

	@GetMapping("/domains")
	List<DomainMessageCount> domains(@AuthenticationPrincipal OidcUser user) {
		return emailMessageRepository.findSenderMessageCounts(user.getEmail())
				.stream()
				.filter(sender -> emailSyncService.senderDomain(sender.sender()) != null)
				.collect(Collectors.groupingBy(
						sender -> emailSyncService.senderDomain(sender.sender()),
						Collectors.summingLong(SenderMessageCount::messageCount)))
				.entrySet()
				.stream()
				.map(entry -> new DomainMessageCount(entry.getKey(), entry.getValue()))
				.sorted(java.util.Comparator
						.comparingLong(DomainMessageCount::messageCount)
						.reversed()
						.thenComparing(DomainMessageCount::domain))
				.toList();
	}

	@DeleteMapping("/senders")
	Map<String, Object> deleteBySender(
			@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
			@AuthenticationPrincipal OidcUser user,
			@RequestParam String sender) {
		int deleted = emailSyncService.trashBySender(
				user.getEmail(),
				authorizedClient.getAccessToken().getTokenValue(),
				sender);
		return Map.of("sender", sender, "deletedMessages", deleted);
	}

	@DeleteMapping("/domains")
	Map<String, Object> deleteByDomain(
			@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
			@AuthenticationPrincipal OidcUser user,
			@RequestParam String domain) {
		int deleted = emailSyncService.trashByDomain(
				user.getEmail(),
				authorizedClient.getAccessToken().getTokenValue(),
				domain);
		return Map.of("domain", domain, "deletedMessages", deleted);
	}

	@DeleteMapping("/{messageId}")
	void deleteMessage(
			@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
			@AuthenticationPrincipal OidcUser user,
			@PathVariable Long messageId) {
		emailSyncService.trashMessage(
				user.getEmail(),
				authorizedClient.getAccessToken().getTokenValue(),
				messageId);
	}

	private Map<String, Long> aggregateByLabel(List<EmailMessage> emails) {
		Map<String, Long> labelCounts = new HashMap<>();
		for (EmailMessage email : emails) {
			for (String label : email.getLabels()) {
				labelCounts.put(label, labelCounts.getOrDefault(label, 0L) + 1);
			}
		}
		return labelCounts;
	}

	private Map<String, Long> aggregateBySender(List<EmailMessage> emails) {
		Map<String, Long> senderCounts = new HashMap<>();
		for (EmailMessage email : emails) {
			String sender = email.getSender();
			if (sender != null) {
				senderCounts.put(sender, senderCounts.getOrDefault(sender, 0L) + 1);
			}
		}
		return senderCounts;
	}

	record SenderPage(
			List<SenderMessageCount> senders,
			int page,
			int totalPages,
			long totalSenders,
			boolean hasNext,
			boolean hasPrevious) {
	}

	record SenderDetails(String sender, long messageCount,
			List<SenderMessageDetail> messages, int page, int totalPages,
			boolean hasNext, boolean hasPrevious) {
	}

	record SenderMessageDetail(Long id, String subject, Instant receivedAt,
			String category, String comment, boolean canDelete, String matchedRule) {
		static SenderMessageDetail from(EmailMessage message) {
			return new SenderMessageDetail(
					message.getId(), message.getSubject(), message.getReceivedAt(),
					message.getRuleCategory(), message.getRuleComment(),
					message.isCanDelete(), message.getMatchedRule());
		}
	}
}
