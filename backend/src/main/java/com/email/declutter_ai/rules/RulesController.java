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
	private final DismissedBaseRuleRepository dismissedBaseRules;
	private final AppParameterService parameters;
	private final RuleEngineService ruleEngine;

	public RulesController(EmailRuleRepository repository,
			DismissedBaseRuleRepository dismissedBaseRules,
			AppParameterService parameters, RuleEngineService ruleEngine) {
		this.repository = repository;
		this.dismissedBaseRules = dismissedBaseRules;
		this.parameters = parameters;
		this.ruleEngine = ruleEngine;
	}

	@GetMapping
	RulesResponse list(@AuthenticationPrincipal OidcUser user) {
		var dismissedIds = dismissedBaseRules.findByAccountEmail(user.getEmail())
				.stream().map(DismissedBaseRule::getRuleId).collect(java.util.stream.Collectors.toSet());
		return new RulesResponse(
				repository.findByAccountEmailIsNullOrderByPriorityDesc().stream()
						.filter(rule -> !dismissedIds.contains(rule.getId())).toList(),
				repository.findByAccountEmailOrderByPriorityDesc(user.getEmail()),
				parameters.autoDeleteRecommended(user.getEmail()));
	}

	@PostMapping
	EmailRule create(@AuthenticationPrincipal OidcUser user,
			@Valid @RequestBody CreateRule request) {
		var saved = repository.save(new EmailRule(
				user.getEmail(), request.name(), request.matchField(),
				request.matchValue(), request.category(), request.comment(),
				request.decision() == EmailRule.Decision.SAFE_TO_DELETE,
				request.priority(), request.subjectContains(),
				request.senderContains(), request.olderThanDays(), request.decision()));
		ruleEngine.reclassifyStoredMessages(user.getEmail());
		return saved;
	}

	@PutMapping("/{id}")
	EmailRule update(@AuthenticationPrincipal OidcUser user, @PathVariable Long id,
			@Valid @RequestBody CreateRule request) {
		var existing = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Rule was not found"));
		EmailRule editable;
		if (user.getEmail().equals(existing.getAccountEmail())) {
			editable = existing;
		}
		else if (existing.getAccountEmail() == null) {
			if (!dismissedBaseRules.existsByAccountEmailAndRuleId(
					user.getEmail(), existing.getId())) {
				dismissedBaseRules.save(new DismissedBaseRule(
						user.getEmail(), existing.getId()));
			}
			editable = new EmailRule(
					user.getEmail(), request.name(), request.matchField(),
					request.matchValue(), request.category(), request.comment(),
					request.decision() == EmailRule.Decision.SAFE_TO_DELETE,
					request.priority(), request.subjectContains(),
					request.senderContains(), request.olderThanDays(),
					request.decision());
		}
		else {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule was not found");
		}
		editable.update(
				request.name(), request.matchField(), request.matchValue(),
				request.category(), request.comment(), request.decision(),
				request.priority(), request.subjectContains(),
				request.senderContains(), request.olderThanDays());
		var saved = repository.save(editable);
		ruleEngine.reclassifyStoredMessages(user.getEmail());
		return saved;
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal OidcUser user, @PathVariable Long id) {
		var userRule = repository.findByIdAndAccountEmail(id, user.getEmail());
		if (userRule.isPresent()) {
			repository.delete(userRule.get());
			ruleEngine.reclassifyStoredMessages(user.getEmail());
			return;
		}
		var baseRule = repository.findById(id)
				.filter(rule -> rule.getAccountEmail() == null)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Rule was not found"));
		if (!dismissedBaseRules.existsByAccountEmailAndRuleId(
				user.getEmail(), baseRule.getId())) {
			dismissedBaseRules.save(new DismissedBaseRule(
					user.getEmail(), baseRule.getId()));
		}
		ruleEngine.reclassifyStoredMessages(user.getEmail());
	}

	@PostMapping("/reapply")
	Map<String, Integer> reapply(@AuthenticationPrincipal OidcUser user) {
		return Map.of("reclassifiedMessages",
				ruleEngine.reclassifyStoredMessages(user.getEmail()));
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
			@NotNull EmailRule.Decision decision,
			@Min(0) @Max(10000) int priority,
			@Size(max = 1000) String subjectContains,
			@Size(max = 1000) String senderContains,
			@Min(1) @Max(36500) Integer olderThanDays) {}

	record AutoDeleteRequest(boolean enabled) {}
}
