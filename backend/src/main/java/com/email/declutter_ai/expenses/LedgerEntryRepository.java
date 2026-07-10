package com.email.declutter_ai.expenses;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
	List<LedgerEntry> findByAccountEmailOrderByDateDescIdDesc(String accountEmail);
	boolean existsByAccountEmail(String accountEmail);
}
