package com.email.declutter_ai.email;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emails")
public class EmailMessageController {

	private final EmailMessageRepository emailMessageRepository;

	public EmailMessageController(EmailMessageRepository emailMessageRepository) {
		this.emailMessageRepository = emailMessageRepository;
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
			emails = emailMessageRepository.findByAccountEmailOrderByReceivedAtDesc(accountEmail, null);
		}

		Map<String, Object> aggregation = new HashMap<>();
		aggregation.put("totalEmails", emails.size());
		aggregation.put("totalSize", emails.stream().mapToInt(e -> e.getSizeEstimate() != null ? e.getSizeEstimate() : 0).sum());
		aggregation.put("averageSize", emails.stream().mapToInt(e -> e.getSizeEstimate() != null ? e.getSizeEstimate() : 0).average().orElse(0));
		aggregation.put("uniqueSenders", emails.stream().map(EmailMessage::getSender).filter(s -> s != null).distinct().count());
		aggregation.put("emailsByLabel", aggregateByLabel(emails));
		aggregation.put("emailsBySender", aggregateBySender(emails));
		
		return aggregation;
	}

	@GetMapping("/stats")
	Map<String, Object> stats(@AuthenticationPrincipal OidcUser user) {
		String accountEmail = user.getEmail();
		List<EmailMessage> emails = emailMessageRepository.findByAccountEmailOrderByReceivedAtDesc(accountEmail, null);

		Map<String, Object> stats = new HashMap<>();
		stats.put("totalEmails", emails.size());
		stats.put("totalSizeBytes", emails.stream().mapToInt(e -> e.getSizeEstimate() != null ? e.getSizeEstimate() : 0).sum());
		stats.put("averageSizeBytes", emails.stream().mapToInt(e -> e.getSizeEstimate() != null ? e.getSizeEstimate() : 0).average().orElse(0));
		stats.put("uniqueSenders", emails.stream().map(EmailMessage::getSender).filter(s -> s != null).distinct().count());
		stats.put("earliestEmail", emails.stream().map(EmailMessage::getReceivedAt).filter(d -> d != null).min(Instant::compareTo).orElse(null));
		stats.put("latestEmail", emails.stream().map(EmailMessage::getReceivedAt).filter(d -> d != null).max(Instant::compareTo).orElse(null));
		
		return stats;
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
}
