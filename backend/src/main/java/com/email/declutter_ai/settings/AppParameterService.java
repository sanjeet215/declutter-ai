package com.email.declutter_ai.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppParameterService {
	public static final String AUTO_DELETE = "autoDeleteRecommended";
	private final AppParameterRepository repository;

	public AppParameterService(AppParameterRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public boolean autoDeleteRecommended(String accountEmail) {
		return repository.findByAccountEmailAndKey(accountEmail, AUTO_DELETE)
				.map(parameter -> Boolean.parseBoolean(parameter.getValue()))
				.orElse(false);
	}

	@Transactional
	public boolean setAutoDeleteRecommended(String accountEmail, boolean enabled) {
		var parameter = repository.findByAccountEmailAndKey(accountEmail, AUTO_DELETE)
				.orElseGet(() -> new AppParameter(accountEmail, AUTO_DELETE, "false"));
		parameter.setValue(Boolean.toString(enabled));
		repository.save(parameter);
		return enabled;
	}
}
