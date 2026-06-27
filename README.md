# Declutter AI

Spring Boot service for connecting a Gmail account and reading message metadata.

## Gmail setup

1. Create a project in Google Cloud Console.
2. Enable the Gmail API.
3. Configure the OAuth consent screen and add your Google account as a test user.
4. Create an OAuth 2.0 Client ID of type **Web application**.
5. Add this authorized redirect URI:

   `http://localhost:8080/login/oauth2/code/google`

6. Start the application with the credentials:

```bash
export GOOGLE_CLIENT_ID="your-client-id"
export GOOGLE_CLIENT_SECRET="your-client-secret"
./gradlew bootRun
```

Open `http://localhost:8080/oauth2/authorization/google` to connect Gmail, then
request `http://localhost:8080/api/gmail/messages`.

The integration requests the `gmail.metadata` scope. It reads headers, labels,
timestamps, and message sizes, but not email bodies or attachments.

After connecting Gmail, open `http://localhost:8080/` and use the sync button.
It upserts 25 messages into PostgreSQL. Stored metadata is available at
`http://localhost:8080/api/gmail/stored`.
