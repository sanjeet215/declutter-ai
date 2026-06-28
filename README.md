# Declutter AI

API-first email cleanup product with a Spring Boot backend and React web client.

## Structure

```text
backend/  Spring Boot API, Gmail integration, PostgreSQL persistence
web/      React + TypeScript + Vite web application
```

## Run the backend

The backend requires Java 21, PostgreSQL, and Google OAuth credentials.

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export GOOGLE_CLIENT_ID="your-client-id"
export GOOGLE_CLIENT_SECRET="your-client-secret"

cd backend
./gradlew bootRun
```

It runs at `http://localhost:8080`.

## Run the web app

In a second terminal:

```bash
cd web
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api`, `/oauth2`, and `/login`
requests to the Spring backend during local development.

## Gmail OAuth setup

Enable the Gmail API, configure the consent screen and test user, and create an
OAuth Web application with this redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

The backend requests `gmail.metadata`, which permits headers, labels, timestamps,
and message sizes but not email bodies or attachments.
