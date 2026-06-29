package com.email.declutter_ai.rules;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRuleRepository extends JpaRepository<EmailRule, Long> {
	List<EmailRule> findByEnabledTrueAndAccountEmailInOrderByPriorityDesc(
			List<String> accountEmails);
	List<EmailRule> findByAccountEmailOrderByPriorityDesc(String accountEmail);
	List<EmailRule> findByAccountEmailIsNullOrderByPriorityDesc();
	boolean existsByAccountEmailIsNullAndName(String name);
	java.util.Optional<EmailRule> findByIdAndAccountEmail(Long id, String accountEmail);
}
