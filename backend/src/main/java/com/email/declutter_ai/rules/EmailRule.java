package com.email.declutter_ai.rules;

import jakarta.persistence.*;

@Entity
@Table(name = "email_rules")
public class EmailRule {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "account_email", length = 320)
	private String accountEmail;
	@Column(nullable = false, length = 120)
	private String name;
	@Enumerated(EnumType.STRING) @Column(name = "match_field", nullable = false, length = 30)
	private MatchField matchField;
	@Column(name = "match_value", nullable = false, length = 1000)
	private String matchValue;
	@Column(nullable = false, length = 100)
	private String category;
	@Column(name = "rule_comment", nullable = false, length = 1000)
	private String comment;
	@Column(name = "can_delete", nullable = false)
	private boolean canDelete;
	@Column(name = "subject_contains", length = 1000)
	private String subjectContains;
	@Column(name = "sender_contains", length = 1000)
	private String senderContains;
	@Column(name = "older_than_days")
	private Integer olderThanDays;
	@Column(nullable = false)
	private boolean enabled = true;
	@Column(nullable = false)
	private int priority;

	protected EmailRule() {}

	public EmailRule(String accountEmail, String name, MatchField matchField,
			String matchValue, String category, String comment,
			boolean canDelete, int priority) {
		this(accountEmail, name, matchField, matchValue, category, comment,
				canDelete, priority, null, null, null);
	}

	public EmailRule(String accountEmail, String name, MatchField matchField,
			String matchValue, String category, String comment,
			boolean canDelete, int priority, String subjectContains,
			String senderContains, Integer olderThanDays) {
		this.accountEmail = accountEmail;
		this.name = name;
		this.matchField = matchField;
		this.matchValue = matchValue;
		this.category = category;
		this.comment = comment;
		this.canDelete = canDelete;
		this.priority = priority;
		this.subjectContains = subjectContains;
		this.senderContains = senderContains;
		this.olderThanDays = olderThanDays;
	}

	public Long getId() { return id; }
	public String getAccountEmail() { return accountEmail; }
	public String getName() { return name; }
	public MatchField getMatchField() { return matchField; }
	public String getMatchValue() { return matchValue; }
	public String getCategory() { return category; }
	public String getComment() { return comment; }
	public boolean isCanDelete() { return canDelete; }
	public boolean isEnabled() { return enabled; }
	public int getPriority() { return priority; }
	public String getSubjectContains() { return subjectContains; }
	public String getSenderContains() { return senderContains; }
	public Integer getOlderThanDays() { return olderThanDays; }

	public enum MatchField { SENDER, DOMAIN, SUBJECT, LABEL }
}
