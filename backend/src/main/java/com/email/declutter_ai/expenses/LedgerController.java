package com.email.declutter_ai.expenses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses/ledger")
public class LedgerController {
	private final LedgerEntryRepository repository;
	private final LedgerRefreshService refreshService;

	public LedgerController(
			LedgerEntryRepository repository,
			LedgerRefreshService refreshService) {
		this.repository = repository;
		this.refreshService = refreshService;
	}

	@GetMapping
	List<LedgerEntry> list(@AuthenticationPrincipal OidcUser user) {
		seedDummyLedgerEntry(user.getEmail());
		return repository.findByAccountEmailOrderByDateDescIdDesc(user.getEmail());
	}

	@PostMapping("/refresh")
	LedgerRefreshService.LedgerRefreshResult refresh(
			@AuthenticationPrincipal OidcUser user) {
		return refreshService.refresh(user.getEmail());
	}

	private void seedDummyLedgerEntry(String accountEmail) {
		if (repository.existsByAccountEmail(accountEmail)) {
			return;
		}
		repository.save(new LedgerEntry(
				accountEmail,
				new BigDecimal("200.00"),
				"INR",
				"6304",
				LocalDate.of(2026, 6, 22),
				"Milkbasket",
				LedgerEntry.TransactionType.DEBIT,
				"HDFC Bank"));
	}
}
