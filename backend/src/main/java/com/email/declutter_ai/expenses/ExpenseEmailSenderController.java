package com.email.declutter_ai.expenses;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/expenses/email-senders")
public class ExpenseEmailSenderController {
	private final ExpenseEmailSenderRepository repository;

	public ExpenseEmailSenderController(ExpenseEmailSenderRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	List<ExpenseEmailSender> list(@AuthenticationPrincipal OidcUser user) {
		return repository.findByAccountEmailOrderByFromEmailAsc(user.getEmail());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ExpenseEmailSender create(@AuthenticationPrincipal OidcUser user,
			@Valid @RequestBody CreateExpenseEmailSender request) {
		String fromEmail = normalize(request.fromEmail());
		if (repository.existsByAccountEmailAndFromEmail(user.getEmail(), fromEmail)) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "Email sender already exists");
		}
		return repository.save(new ExpenseEmailSender(user.getEmail(), fromEmail));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal OidcUser user, @PathVariable Long id) {
		var sender = repository.findByIdAndAccountEmail(id, user.getEmail())
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Email sender was not found"));
		repository.delete(sender);
	}

	private String normalize(String fromEmail) {
		return fromEmail.trim().toLowerCase(Locale.ROOT);
	}

	record CreateExpenseEmailSender(
			@NotBlank @Email @Size(max = 320) String fromEmail) {}
}
