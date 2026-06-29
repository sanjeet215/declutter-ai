package com.email.declutter_ai.settings;

import jakarta.persistence.*;

@Entity
@Table(name = "app_parameters", uniqueConstraints = @UniqueConstraint(
		name = "uk_app_parameter_account_key",
		columnNames = {"account_email", "parameter_key"}))
public class AppParameter {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "account_email", nullable = false, length = 320)
	private String accountEmail;
	@Column(name = "parameter_key", nullable = false, length = 100)
	private String key;
	@Column(name = "parameter_value", nullable = false, length = 1000)
	private String value;

	protected AppParameter() {}
	public AppParameter(String accountEmail, String key, String value) {
		this.accountEmail = accountEmail;
		this.key = key;
		this.value = value;
	}
	public String getKey() { return key; }
	public String getValue() { return value; }
	public void setValue(String value) { this.value = value; }
}
