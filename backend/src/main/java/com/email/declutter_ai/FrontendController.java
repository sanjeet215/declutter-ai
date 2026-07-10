package com.email.declutter_ai;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

	private final URI frontendUri;

	public FrontendController(@Value("${app.frontend-url}") String frontendUrl) {
		this.frontendUri = URI.create(frontendUrl);
	}

	@GetMapping("/")
	ResponseEntity<Void> frontend() {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(frontendUri)
				.build();
	}

	@GetMapping("/classification")
	ResponseEntity<Void> classification() {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(frontendUri.resolve("/classification"))
				.build();
	}

	@GetMapping("/sender")
	ResponseEntity<Void> sender() {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(frontendUri.resolve("/sender"))
				.build();
	}

	@GetMapping("/drive")
	ResponseEntity<Void> drive() {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(frontendUri.resolve("/drive"))
				.build();
	}

	@GetMapping("/drive/untitled")
	ResponseEntity<Void> driveUntitled() {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(frontendUri.resolve("/drive/untitled"))
				.build();
	}

	@GetMapping("/drive/files")
	ResponseEntity<Void> driveFiles() {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(frontendUri.resolve("/drive/files"))
				.build();
	}

	@GetMapping("/photos")
	ResponseEntity<Void> photos() {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(frontendUri.resolve("/photos"))
				.build();
	}
}
