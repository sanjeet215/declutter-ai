package com.email.declutter_ai.email;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

	Optional<EmailMessage> findByAccountEmailAndGmailMessageId(
			String accountEmail, String gmailMessageId);

	Page<EmailMessage> findByAccountEmailOrderByReceivedAtDesc(
			String accountEmail, Pageable pageable);

	List<EmailMessage> findByAccountEmailOrderByReceivedAtDesc(String accountEmail);

	List<EmailMessage> findByAccountEmailAndReceivedAtBetweenOrderByReceivedAtDesc(
			String accountEmail, Instant startDate, Instant endDate);
}
