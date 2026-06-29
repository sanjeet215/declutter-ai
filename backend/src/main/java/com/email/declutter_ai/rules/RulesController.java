package com.email.declutter_ai.rules;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.email.declutter_ai.settings.AppParameterService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/rules")
public class RulesController {
	private final EmailRuleRepository repository;
	private final AppParameterService parameters;

	public RulesController(EmailRuleRepository repository,
			AppParameterService parameters) {
		this.repository = repository;
		this.parameters = parameters;
	}

	@GetMapping
	RulesResponse list(@AuthenticationPrincipal OidcUser user) {
		return new RulesResponse(
				repository.findByAccountEmailIsNullOrderByPriorityDesc(),
				repository.findByAccountEmailOrderByPriorityDesc(user.getEmail()),
				parameters.autoDeleteRecommended(user.getEmail()));
	}

	@PostMapping
	EmailRule create(@AuthenticationPrincipal OidcUser user,
			@Valid @RequestBody CreateRule request) {
		return repository.save(new EmailRule(
				user.getEmail(), request.name(), request.matchField(),
				request.matchValue(), request.category(), request.comment(),
				request.canDelete(), request.priority(), request.subjectContains(),
				request.senderContains(), request.olderThanDays()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal OidcUser user, @PathVariable Long id) {
		var rule = repository.findByIdAndAccountEmail(id, user.getEmail())
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Rule was not found"));
		repository.delete(rule);
	}

	@PutMapping("/settings/auto-delete")
	Map<String, Boolean> autoDelete(@AuthenticationPrincipal OidcUser user,
			@RequestBody AutoDeleteRequest request) {
		return Map.of(AppParameterService.AUTO_DELETE,
				parameters.setAutoDeleteRecommended(user.getEmail(), request.enabled()));
	}

	record RulesResponse(List<EmailRule> baseRules, List<EmailRule> userRules,
			boolean autoDeleteRecommended) {}

	record CreateRule(
			@NotBlank @Size(max = 120) String name,
			@NotNull EmailRule.MatchField matchField,
			@NotBlank @Size(max = 1000) String matchValue,
			@NotBlank @Size(max = 100) String category,
			@NotBlank @Size(max = 1000) String comment,
			boolean canDelete,
			@Min(0) @Max(10000) int priority,
			@Size(max = 1000) String subjectContains,
			@Size(max = 1000) String senderContains,
			@Min(1) @Max(36500) Integer olderThanDays) {}

	record AutoDeleteRequest(boolean enabled) {}
}
