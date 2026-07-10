package com.email.declutter_ai.expenses;

import org.springframework.stereotype.Service;

@Service
public class LedgerRefreshService {
	public LedgerRefreshResult refresh(String accountEmail) {
		return new LedgerRefreshResult(0, "Dummy ledger refresh completed.");
	}

	public record LedgerRefreshResult(int processedMessages, String message) {}
}
