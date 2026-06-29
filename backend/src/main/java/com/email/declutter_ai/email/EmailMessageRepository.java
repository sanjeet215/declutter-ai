package com.email.declutter_ai.email;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

	Optional<EmailMessage> findByAccountEmailAndGmailMessageId(
			String accountEmail, String gmailMessageId);

	Page<EmailMessage> findByAccountEmailOrderByReceivedAtDesc(
			String accountEmail, Pageable pageable);

	List<EmailMessage> findByAccountEmailOrderByReceivedAtDesc(String accountEmail);

	List<EmailMessage> findByAccountEmailAndReceivedAtBetweenOrderByReceivedAtDesc(
			String accountEmail, Instant startDate, Instant endDate);

	@Query("""
			select new com.email.declutter_ai.email.SenderMessageCount(
				message.sender, count(message)
			)
			from EmailMessage message
			where message.accountEmail = :accountEmail
				and message.sender is not null
				and trim(message.sender) <> ''
			group by message.sender
			order by count(message) desc, message.sender asc
			""")
	List<SenderMessageCount> findSenderMessageCounts(
			@Param("accountEmail") String accountEmail);

	@Query(value = """
			select new com.email.declutter_ai.email.SenderMessageCount(
				message.sender, count(message)
			)
			from EmailMessage message
			where message.accountEmail = :accountEmail
				and message.sender is not null
				and trim(message.sender) <> ''
			group by message.sender
			order by count(message) desc, message.sender asc
			""", countQuery = """
			select count(distinct message.sender)
			from EmailMessage message
			where message.accountEmail = :accountEmail
				and message.sender is not null
				and trim(message.sender) <> ''
			""")
	Page<SenderMessageCount> findSenderMessageCountsPage(
			@Param("accountEmail") String accountEmail, Pageable pageable);

	List<EmailMessage> findByAccountEmailAndSender(
			String accountEmail, String sender);

	Optional<EmailMessage> findByIdAndAccountEmail(
			Long id, String accountEmail);

	@Query("""
			select coalesce(sum(message.sizeEstimate), 0)
			from EmailMessage message
			where message.accountEmail = :accountEmail
			""")
	long sumSizeEstimateByAccountEmail(@Param("accountEmail") String accountEmail);
}
