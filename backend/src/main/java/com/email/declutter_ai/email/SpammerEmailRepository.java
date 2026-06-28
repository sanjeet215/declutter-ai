package com.email.declutter_ai.email;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpammerEmailRepository extends JpaRepository<SpammerEmail, Long> {

	Optional<SpammerEmail> findByAccountEmailAndSenderEmail(
			String accountEmail, String senderEmail);
}
