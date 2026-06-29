package com.email.declutter_ai.rules;

import java.util.ArrayList;
import java.util.Locale;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.email.declutter_ai.email.EmailMessage;

@Service
public class RuleEngineService implements ApplicationRunner {
	private final EmailRuleRepository repository;

	public RuleEngineService(EmailRuleRepository repository) {
		this.repository = repository;
	}

	@Override @Transactional
	public void run(ApplicationArguments args) {
		addBase("Transaction alerts", EmailRule.MatchField.SUBJECT, "transaction alert",
				"Transaction Alert",
				"Bank or payment transaction notification; review before deleting.",
				false, 400);
		addBase("Receipts and invoices", EmailRule.MatchField.SUBJECT, "receipt",
				"Finance", "Likely a receipt; keep for your records.", false, 300);
		addBase("Promotions", EmailRule.MatchField.LABEL, "CATEGORY_PROMOTIONS",
				"Promotion", "Gmail classified this as promotional; it can usually be deleted.", true, 100);
		addBase("Automated mail", EmailRule.MatchField.SENDER, "no-reply",
				"Automated", "Automated sender; review before deleting.", false, 50);
	}

	private void addBase(String name, EmailRule.MatchField field, String value,
			String category, String comment, boolean canDelete, int priority) {
		if (!repository.existsByAccountEmailIsNullAndName(name)) {
			repository.save(new EmailRule(null, name, field, value, category,
					comment, canDelete, priority));
		}
	}

	@Transactional(readOnly = true)
	public Classification classify(String accountEmail, EmailMessage message) {
		var rules = new ArrayList<EmailRule>();
		rules.addAll(repository.findByAccountEmailOrderByPriorityDesc(accountEmail));
		rules.addAll(repository.findByAccountEmailIsNullOrderByPriorityDesc());
		return rules.stream().filter(EmailRule::isEnabled)
				.filter(rule -> matches(rule, message))
				.findFirst()
				.map(rule -> new Classification(rule.getCategory(), rule.getComment(),
						rule.isCanDelete(), rule.getName()))
				.orElse(new Classification("Uncategorized",
						"No rule matched this message.", false, null));
	}

	private boolean matches(EmailRule rule, EmailMessage message) {
		String needle = rule.getMatchValue().toLowerCase(Locale.ROOT);
		boolean primaryMatches = switch (rule.getMatchField()) {
			case SENDER -> contains(message.getSender(), needle);
			case SUBJECT -> contains(message.getSubject(), needle);
			case LABEL -> message.getLabels().stream()
					.anyMatch(label -> label.equalsIgnoreCase(rule.getMatchValue()));
			case DOMAIN -> {
				String sender = message.getSender();
				yield sender != null && sender.toLowerCase(Locale.ROOT)
						.matches(".*@" + java.util.regex.Pattern.quote(needle) + "[>\\s]*$");
			}
		};
		boolean subjectMatches = rule.getSubjectContains() == null
				|| contains(message.getSubject(),
						rule.getSubjectContains().toLowerCase(Locale.ROOT));
		boolean senderMatches = rule.getSenderContains() == null
				|| contains(message.getSender(),
						rule.getSenderContains().toLowerCase(Locale.ROOT));
		boolean ageMatches = rule.getOlderThanDays() == null
				|| (message.getReceivedAt() != null
						&& message.getReceivedAt().isBefore(
								Instant.now().minus(rule.getOlderThanDays(), ChronoUnit.DAYS)));
		return primaryMatches && subjectMatches && senderMatches && ageMatches;
	}

	private boolean contains(String value, String needle) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
	}

	public record Classification(String category, String comment,
			boolean canDelete, String ruleName) {}
}
