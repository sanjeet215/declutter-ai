package com.email.declutter_ai.email;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "spammer_emails", uniqueConstraints = @UniqueConstraint(
		name = "uk_spammer_email_account_sender",
		columnNames = {"account_email", "sender_email"}))
public class SpammerEmail {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_email", nullable = false, length = 320)
	private String accountEmail;

	@Column(name = "sender_email", nullable = false, length = 320)
	private String senderEmail;

	@Column(name = "sender_header", nullable = false, length = 1000)
	private String senderHeader;

	@Column(name = "marked_at", nullable = false)
	private Instant markedAt;

	protected SpammerEmail() {
	}

	public SpammerEmail(
			String accountEmail,
			String senderEmail,
			String senderHeader,
			Instant markedAt) {
		this.accountEmail = accountEmail;
		this.senderEmail = senderEmail;
		this.senderHeader = senderHeader;
		this.markedAt = markedAt;
	}

	public void markAgain(String senderHeader, Instant markedAt) {
		this.senderHeader = senderHeader;
		this.markedAt = markedAt;
	}
}
