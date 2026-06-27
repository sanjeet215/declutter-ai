package com.email.declutter_ai;

import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

	@GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
	@ResponseBody
	String home(CsrfToken csrfToken) {
		return """
				<!doctype html>
				<html lang="en">
				<head><meta charset="utf-8"><title>Declutter AI</title></head>
				<body>
				  <h1>Declutter AI</h1>
				  <p><a href="/oauth2/authorization/google">Connect Gmail</a></p>
				  <p><a href="/api/gmail/messages">Preview Gmail metadata</a></p>
				  <form action="/api/gmail/sync" method="post">
				    <input type="hidden" name="%s" value="%s">
				    <button type="submit">Sync 25 messages to PostgreSQL</button>
				  </form>
				  <p><a href="/api/gmail/stored">View stored metadata</a></p>
				</body>
				</html>
				""".formatted(csrfToken.getParameterName(), csrfToken.getToken());
	}
}
