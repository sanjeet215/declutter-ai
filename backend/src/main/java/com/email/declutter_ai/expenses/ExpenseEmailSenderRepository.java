package com.email.declutter_ai.expenses;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseEmailSenderRepository
		extends JpaRepository<ExpenseEmailSender, Long> {
	List<ExpenseEmailSender> findByAccountEmailOrderByFromEmailAsc(String accountEmail);
	Optional<ExpenseEmailSender> findByIdAndAccountEmail(Long id, String accountEmail);
	boolean existsByAccountEmailAndFromEmail(String accountEmail, String fromEmail);
}
