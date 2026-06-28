package com.email.declutter_ai.email;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.email.declutter_ai.gmail.GmailClient.EmailMetadata;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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

	@Column(name = "recipient_to", length = 4000)
	private String recipientTo;

	@Column(name = "recipient_cc", length = 4000)
	private String recipientCc;

	@Column(name = "recipient_bcc", length = 4000)
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

	protected EmailMessage() {
	}

	public EmailMessage(String accountEmail, String gmailMessageId) {
		this.accountEmail = accountEmail;
		this.gmailMessageId = gmailMessageId;
	}

	public void updateFrom(EmailMetadata metadata, Instant syncTime) {
		gmailThreadId = metadata.threadId();
		internetMessageId = metadata.internetMessageId();
		sender = metadata.from();
		recipientTo = metadata.to();
		recipientCc = metadata.cc();
		recipientBcc = metadata.bcc();
		subject = metadata.subject();
		dateHeader = metadata.dateHeader();
		receivedAt = metadata.receivedAt();
		sizeEstimate = metadata.sizeEstimate();
		labels.clear();
		labels.addAll(metadata.labelIds());
		syncedAt = syncTime;
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
}
