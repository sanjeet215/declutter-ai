package com.email.declutter_ai.rules;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DismissedBaseRuleRepository
		extends JpaRepository<DismissedBaseRule, Long> {
	List<DismissedBaseRule> findByAccountEmail(String accountEmail);
	boolean existsByAccountEmailAndRuleId(String accountEmail, Long ruleId);
}
