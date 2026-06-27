package com.email.declutter_ai.gmail;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GmailClient {

	private static final String[] METADATA_HEADERS = {
			"From", "To", "Cc", "Bcc", "Subject", "Date", "Message-ID"
	};

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

		List<EmailMetadata> messages = list == null || list.messages() == null
				? List.of()
				: list.messages().stream()
						.map(message -> getMetadata(accessToken, message.id()))
						.toList();

		return new MessagePage(messages, list == null ? null : list.nextPageToken(),
				list == null ? 0 : list.resultSizeEstimate());
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
