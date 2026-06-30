package com.email.declutter_ai.email;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.email.declutter_ai.gmail.GmailClient.EmailMetadata;
import com.email.declutter_ai.rules.EmailRule.Decision;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "email_messages", uniqueConstraints = @UniqueConstraint(
		name = "uk_email_message_account_gmail_id",
		columnNames = {"account_email", "gmail_message_id"}))
public class EmailMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_email", nullable = false, length = 320)
	private String accountEmail;

	@Column(name = "gmail_message_id", nullable = false, length = 64)
	private String gmailMessageId;

	@Column(name = "gmail_thread_id", length = 64)
	private String gmailThreadId;

	@Column(name = "internet_message_id", length = 1000)
	private String internetMessageId;

	@Column(name = "sender", length = 1000)
	private String sender;

	@Column(name = "recipient_to", columnDefinition = "text")
	private String recipientTo;

	@Column(name = "recipient_cc", columnDefinition = "text")
	private String recipientCc;

	@Column(name = "recipient_bcc", columnDefinition = "text")
	private String recipientBcc;

	@Column(length = 2000)
	private String subject;

	@Column(name = "date_header", length = 255)
	private String dateHeader;

	@Column(name = "received_at")
	private Instant receivedAt;

	@Column(name = "size_estimate")
	private Integer sizeEstimate;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "email_message_labels",
			joinColumns = @JoinColumn(name = "email_message_id"))
	@Column(name = "label", nullable = false, length = 255)
	private List<String> labels = new ArrayList<>();

	@Column(name = "synced_at", nullable = false)
	private Instant syncedAt;

	@Column(name = "rule_category", length = 100)
	private String ruleCategory;

	@Column(name = "rule_comment", length = 1000)
	private String ruleComment;

	@Column(name = "can_delete")
	private Boolean canDelete;

	@Column(name = "matched_rule", length = 120)
	private String matchedRule;
	@Enumerated(jakarta.persistence.EnumType.STRING)
	@Column(name = "rule_decision", length = 30)
	private Decision ruleDecision;

	protected EmailMessage() {
	}

	public EmailMessage(String accountEmail, String gmailMessageId) {
		this.accountEmail = accountEmail;
		this.gmailMessageId = gmailMessageId;
	}

	public void updateFrom(EmailMetadata metadata, Instant syncTime) {
		gmailThreadId = truncate(metadata.threadId(), 64);
		internetMessageId = truncate(metadata.internetMessageId(), 1000);
		sender = truncate(metadata.from(), 1000);
		recipientTo = truncate(metadata.to(), 4000);
		recipientCc = truncate(metadata.cc(), 4000);
		recipientBcc = truncate(metadata.bcc(), 4000);
		subject = truncate(metadata.subject(), 2000);
		dateHeader = truncate(metadata.dateHeader(), 255);
		receivedAt = metadata.receivedAt();
		sizeEstimate = metadata.sizeEstimate();
		labels.clear();
		metadata.labelIds().stream()
				.map(label -> truncate(label, 255))
				.forEach(labels::add);
		syncedAt = syncTime;
	}

	private static String truncate(String value, int maxCharacters) {
		if (value == null || value.codePointCount(0, value.length()) <= maxCharacters) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxCharacters));
	}

	public void applyClassification(String category, String comment,
			Decision decision, String ruleName) {
		this.ruleCategory = truncate(category, 100);
		this.ruleComment = truncate(comment, 1000);
		this.ruleDecision = decision;
		this.canDelete = decision == Decision.SAFE_TO_DELETE;
		this.matchedRule = truncate(ruleName, 120);
	}

	public Long getId() {
		return id;
	}

	public String getAccountEmail() {
		return accountEmail;
	}

	public String getGmailMessageId() {
		return gmailMessageId;
	}

	public String getGmailThreadId() {
		return gmailThreadId;
	}

	public String getInternetMessageId() {
		return internetMessageId;
	}

	public String getSender() {
		return sender;
	}

	public String getRecipientTo() {
		return recipientTo;
	}

	public String getRecipientCc() {
		return recipientCc;
	}

	public String getRecipientBcc() {
		return recipientBcc;
	}

	public String getSubject() {
		return subject;
	}

	public String getDateHeader() {
		return dateHeader;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public Integer getSizeEstimate() {
		return sizeEstimate;
	}

	public List<String> getLabels() {
		return List.copyOf(labels);
	}

	public Instant getSyncedAt() {
		return syncedAt;
	}

	public String getRuleCategory() { return ruleCategory; }
	public String getRuleComment() { return ruleComment; }
	public boolean isCanDelete() { return Boolean.TRUE.equals(canDelete); }
	public Decision getRuleDecision() {
		if (ruleDecision != null) return ruleDecision;
		return isCanDelete() ? Decision.SAFE_TO_DELETE : Decision.REVIEW;
	}
	public String getMatchedRule() { return matchedRule; }
}
