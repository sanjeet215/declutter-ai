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
}
