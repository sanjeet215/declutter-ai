package com.email.declutter_ai.gmail;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GmailClient {

	private static final String[] METADATA_HEADERS = {
			"From", "To", "Cc", "Bcc", "Subject", "Date", "Message-ID"
	};
	private static final int MAX_CONCURRENT_METADATA_REQUESTS = 10;
	private static final int GMAIL_MAX_PAGE_SIZE = 500;
	private static final Pattern EMAIL_IN_ANGLE_BRACKETS =
			Pattern.compile("<\\s*([^<>\\s]+@[^<>\\s]+)\\s*>");

	private final RestClient restClient;

	public GmailClient(RestClient.Builder builder,
			@Value("${gmail.api-base-url}") String apiBaseUrl) {
		this.restClient = builder.baseUrl(apiBaseUrl).build();
	}

	public MessagePage listMetadata(String accessToken, int maxResults, String pageToken) {
		MessageList list = restClient.get()
				.uri(uriBuilder -> {
					var builder = uriBuilder
							.path("/gmail/v1/users/me/messages")
							.queryParam("maxResults", maxResults);
					if (pageToken != null && !pageToken.isBlank()) {
						builder.queryParam("pageToken", pageToken);
					}
					return builder.build();
				})
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve()
				.body(MessageList.class);

		List<EmailMetadata> messages = fetchMetadataConcurrently(
				accessToken,
				list == null ? null : list.messages());

		return new MessagePage(messages, list == null ? null : list.nextPageToken(),
				list == null ? 0 : list.resultSizeEstimate());
	}

	private List<EmailMetadata> fetchMetadataConcurrently(
			String accessToken, List<MessageReference> messageReferences) {
		if (messageReferences == null || messageReferences.isEmpty()) {
			return List.of();
		}

		int concurrency = Math.min(
				MAX_CONCURRENT_METADATA_REQUESTS,
				messageReferences.size());
		try (var executor = Executors.newFixedThreadPool(concurrency)) {
			var futures = messageReferences.stream()
					.map(message -> executor.submit(
							() -> getMetadata(accessToken, message.id())))
					.toList();
			return futures.stream()
					.map(this::getCompletedMetadata)
					.toList();
		}
	}

	private EmailMetadata getCompletedMetadata(
			java.util.concurrent.Future<EmailMetadata> future) {
		try {
			return future.get();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Gmail metadata sync was interrupted", exception);
		}
		catch (ExecutionException exception) {
			throw new IllegalStateException("Unable to retrieve Gmail metadata",
					exception.getCause());
		}
	}

	public void trashMessage(String accessToken, String messageId) {
		restClient.post()
				.uri("/gmail/v1/users/me/messages/{id}/trash", messageId)
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve()
				.toBodilessEntity();
	}

	public int trashMessagesFromSender(
			String accessToken, String sender, List<String> knownMessageIds) {
		return trashMessagesMatching(
				accessToken,
				"from:\"" + escapeQuery(sender) + "\"",
				knownMessageIds);
	}

	public int trashMessages(String accessToken, List<String> messageIds) {
		if (messageIds.isEmpty()) {
			return 0;
		}
		int concurrency = Math.min(
				MAX_CONCURRENT_METADATA_REQUESTS,
				messageIds.size());
		try (var executor = Executors.newFixedThreadPool(concurrency)) {
			var futures = messageIds.stream()
					.distinct()
					.map(messageId -> executor.submit(() -> {
						trashMessage(accessToken, messageId);
						return (Void) null;
					}))
					.toList();
			futures.forEach(this::waitForTrashRequest);
			return futures.size();
		}
	}

	public int trashMessagesFromDomain(
			String accessToken, String domain, List<String> knownMessageIds) {
		String normalizedDomain = domain.trim().toLowerCase();
		List<MessageReference> candidates = listAllMessagesMatching(
				accessToken,
				"\"" + escapeQuery(normalizedDomain) + "\"");
		var messageIds = new java.util.LinkedHashSet<>(knownMessageIds);
		fetchMetadataConcurrently(accessToken, candidates).stream()
				.filter(message -> matchesDomain(message.from(), normalizedDomain))
				.map(EmailMetadata::id)
				.forEach(messageIds::add);
		return trashMessages(accessToken, List.copyOf(messageIds));
	}

	private int trashMessagesMatching(
			String accessToken, String query, List<String> knownMessageIds) {
		List<MessageReference> messages = listAllMessagesMatching(
				accessToken, query);
		var messageIds = new java.util.LinkedHashSet<>(knownMessageIds);
		messages.stream()
				.map(MessageReference::id)
				.forEach(messageIds::add);
		return trashMessages(accessToken, List.copyOf(messageIds));
	}

	private List<MessageReference> listAllMessagesMatching(
			String accessToken, String query) {
		var messages = new java.util.ArrayList<MessageReference>();
		String pageToken = null;
		do {
			String currentPageToken = pageToken;
			MessageList page = restClient.get()
					.uri(uriBuilder -> {
						var builder = uriBuilder
								.path("/gmail/v1/users/me/messages")
								.queryParam("maxResults", GMAIL_MAX_PAGE_SIZE)
								.queryParam("q", query);
						if (currentPageToken != null) {
							builder.queryParam("pageToken", currentPageToken);
						}
						return builder.build();
					})
					.headers(headers -> headers.setBearerAuth(accessToken))
					.retrieve()
					.body(MessageList.class);
			if (page == null) {
				break;
			}
			if (page.messages() != null) {
				messages.addAll(page.messages());
			}
			pageToken = page.nextPageToken();
		}
		while (pageToken != null && !pageToken.isBlank());
		return messages;
	}

	private String escapeQuery(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private boolean matchesDomain(String sender, String selectedDomain) {
		if (sender == null) {
			return false;
		}
		var matcher = EMAIL_IN_ANGLE_BRACKETS.matcher(sender);
		String address = matcher.find() ? matcher.group(1) : sender.trim();
		int at = address.lastIndexOf('@');
		if (at < 0 || at + 1 >= address.length()) {
			return false;
		}
		String senderDomain = address.substring(at + 1)
				.replaceAll("[>\\s]+$", "")
				.toLowerCase();
		return senderDomain.equals(selectedDomain)
				|| senderDomain.endsWith("." + selectedDomain);
	}

	private void waitForTrashRequest(java.util.concurrent.Future<Void> future) {
		try {
			future.get();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Gmail trash operation was interrupted", exception);
		}
		catch (ExecutionException exception) {
			if (exception.getCause() instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new IllegalStateException("Unable to move Gmail message to Trash",
					exception.getCause());
		}
	}

	private EmailMetadata getMetadata(String accessToken, String messageId) {
		GmailMessage message = restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/gmail/v1/users/me/messages/{id}")
						.queryParam("format", "metadata")
						.queryParam("metadataHeaders", (Object[]) METADATA_HEADERS)
						.build(messageId))
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve()
				.body(GmailMessage.class);

		if (message == null) {
			throw new IllegalStateException("Gmail returned an empty message response");
		}

		Map<String, String> headers = message.payload() == null || message.payload().headers() == null
				? Map.of()
				: message.payload().headers().stream()
						.collect(Collectors.toMap(
								header -> header.name().toLowerCase(),
								Header::value,
								(first, ignored) -> first));

		return new EmailMetadata(
				message.id(),
				message.threadId(),
				value(headers, "message-id"),
				value(headers, "from"),
				value(headers, "to"),
				value(headers, "cc"),
				value(headers, "bcc"),
				value(headers, "subject"),
				value(headers, "date"),
				message.internalDate() == null ? null
						: Instant.ofEpochMilli(Long.parseLong(message.internalDate())),
				message.labelIds() == null ? List.of() : message.labelIds(),
				message.sizeEstimate());
	}

	private String value(Map<String, String> headers, String name) {
		return headers.get(name);
	}

	public record MessagePage(
			List<EmailMetadata> messages,
			String nextPageToken,
			int resultSizeEstimate) {
	}

	public record EmailMetadata(
			String id,
			String threadId,
			String internetMessageId,
			String from,
			String to,
			String cc,
			String bcc,
			String subject,
			String dateHeader,
			Instant receivedAt,
			List<String> labelIds,
			Integer sizeEstimate) {
	}

	private record MessageList(
			List<MessageReference> messages,
			String nextPageToken,
			int resultSizeEstimate) {
	}

	private record MessageReference(String id, String threadId) {
	}

	private record GmailMessage(
			String id,
			String threadId,
			List<String> labelIds,
			String internalDate,
			Integer sizeEstimate,
			Payload payload) {
	}

	private record Payload(List<Header> headers) {
	}

	private record Header(String name, String value) {
	}
}
