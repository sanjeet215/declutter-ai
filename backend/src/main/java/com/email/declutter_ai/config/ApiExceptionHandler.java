package com.email.declutter_ai.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import com.email.declutter_ai.storage.StorageAccessException;
import com.email.declutter_ai.photos.PhotosAccessException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(HttpClientErrorException.Forbidden.class)
	ResponseEntity<Map<String, String>> gmailPermissionDenied(
			HttpClientErrorException.Forbidden exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of(
						"error", "gmail_permission_required",
						"message",
						"Reconnect Gmail and approve permission to move messages to Trash."));
	}

	@ExceptionHandler(StorageAccessException.class)
	ResponseEntity<Map<String, String>> storageAccessDenied(
			StorageAccessException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of(
						"error", "storage_access_required",
						"message", exception.getMessage()));
	}

	@ExceptionHandler(PhotosAccessException.class)
	ResponseEntity<Map<String, String>> photosAccessDenied(
			PhotosAccessException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of(
						"error", "photos_access_required",
						"message", exception.getMessage()));
	}
}
