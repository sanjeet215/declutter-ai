package com.email.declutter_ai.expenses;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "expense_email_senders", uniqueConstraints = @UniqueConstraint(
		name = "uk_expense_email_sender_account_from",
		columnNames = {"account_email", "from_email"}))
public class ExpenseEmailSender {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "account_email", nullable = false, length = 320)
	private String accountEmail;
	@Column(name = "from_email", nullable = false, length = 320)
	private String fromEmail;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ExpenseEmailSender() {}

	public ExpenseEmailSender(String accountEmail, String fromEmail) {
		this.accountEmail = accountEmail;
		this.fromEmail = fromEmail;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public String getAccountEmail() { return accountEmail; }
	public String getFromEmail() { return fromEmail; }
	public Instant getCreatedAt() { return createdAt; }
}
