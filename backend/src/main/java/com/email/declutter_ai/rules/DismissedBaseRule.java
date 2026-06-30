package com.email.declutter_ai.rules;

import jakarta.persistence.*;

@Entity
@Table(name = "dismissed_base_rules", uniqueConstraints = @UniqueConstraint(
		name = "uk_dismissed_base_rule_account_rule",
		columnNames = {"account_email", "rule_id"}))
public class DismissedBaseRule {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "account_email", nullable = false, length = 320)
	private String accountEmail;
	@Column(name = "rule_id", nullable = false)
	private Long ruleId;

	protected DismissedBaseRule() {}
	public DismissedBaseRule(String accountEmail, Long ruleId) {
		this.accountEmail = accountEmail;
		this.ruleId = ruleId;
	}
	public Long getRuleId() { return ruleId; }
}
