package com.email.declutter_ai.expenses;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "account_email", nullable = false, length = 320)
	private String accountEmail;
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;
	@Column(nullable = false, length = 3)
	private String currency;
	@Column(name = "account_ending", nullable = false, length = 12)
	private String accountEnding;
	@Column(name = "transaction_date", nullable = false)
	private LocalDate date;
	@Column(name = "spent_on", nullable = false, length = 200)
	private String spentOn;
	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 20)
	private TransactionType transactionType;
	@Column(nullable = false, length = 120)
	private String bank;

	protected LedgerEntry() {}

	public LedgerEntry(String accountEmail, BigDecimal amount, String currency,
			String accountEnding, LocalDate date, String spentOn,
			TransactionType transactionType, String bank) {
		this.accountEmail = accountEmail;
		this.amount = amount;
		this.currency = currency;
		this.accountEnding = accountEnding;
		this.date = date;
		this.spentOn = spentOn;
		this.transactionType = transactionType;
		this.bank = bank;
	}

	public Long getId() { return id; }
	public String getAccountEmail() { return accountEmail; }
	public BigDecimal getAmount() { return amount; }
	public String getCurrency() { return currency; }
	public String getAccountEnding() { return accountEnding; }
	public LocalDate getDate() { return date; }
	public String getSpentOn() { return spentOn; }
	public TransactionType getTransactionType() { return transactionType; }
	public String getBank() { return bank; }

	public enum TransactionType { DEBIT, CREDIT }
}
