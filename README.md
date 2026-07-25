# AI Pull Request Reviewer

Paste a GitHub PR URL, get back an AI-generated review: an overall risk level,
a summary, and specific findings per file/line.

- **Backend:** Spring Boot 3 (Java 17) — fetches the PR diff from GitHub and sends
  it to the Groq API (OpenAI-compatible, free tier) for review.
- **Frontend:** React (Vite) — a single-page form + results view.

This is the v1 MVP: stateless (nothing is persisted), and the LLM does all of the
analysis (no separate rule engine / static analysis pass yet — see "Roadmap" below).

## How it works

```
React UI  --PR URL-->  Spring Boot  --diff-->  GitHub REST API
                              |
                              v
                         Groq API  --structured JSON review-->  Spring Boot --> React UI
```

1. User pastes a PR URL like `https://github.com/owner/repo/pull/123`.
2. Backend fetches the unified diff from GitHub's REST API.
3. Backend sends the diff to Groq with a system prompt asking for a structured
   JSON review (risk level, summary, findings).
4. Backend returns that JSON to the frontend, which renders it.

## Prerequisites

- Java 17+
- Maven 3.9+ (or use the included setup with your IDE)
- Node.js 18+
- A free Groq API key from https://console.groq.com/keys
- (Optional but recommended) a GitHub personal access token, to avoid the 60
  requests/hour unauthenticated rate limit — needed at all for private repos.

## Backend setup

```bash
cd backend
export GROQ_API_KEY=gsk_...
export GITHUB_TOKEN=ghp_...                     # optional for public repos
export GROQ_MODEL=llama-3.3-70b-versatile       # optional, this is the default
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

### Endpoint

`POST /api/review`

```json
{ "prUrl": "https://github.com/owner/repo/pull/123" }
```

Response:

```json
{
  "prUrl": "https://github.com/owner/repo/pull/123",
  "riskLevel": "HIGH",
  "summary": "...",
  "findings": [
    { "file": "src/main/...", "line": 42, "severity": "HIGH", "comment": "..." }
  ]
}
```

## Frontend setup

```bash
cd frontend
cp .env.example .env   # adjust VITE_API_BASE_URL if the backend isn't on :8080
npm install
npm run dev
```

Open `http://localhost:5173`, paste a PR URL, and click "Review PR".

## Configuration reference

| Variable                | Where    | Default                 | Purpose                                   |
|--------------------------|----------|--------------------------|--------------------------------------------|
| `GROQ_API_KEY`           | backend  | *(required)*             | Groq auth                                  |
| `GROQ_MODEL`             | backend  | `llama-3.3-70b-versatile` | Model used for review                      |
| `GITHUB_TOKEN`           | backend  | *(empty)*                | Raises GitHub API rate limit / private repo access |
| `MAX_DIFF_CHARS`         | backend  | `24000`                  | Diff is truncated to this length before sending to Groq, to stay under free-tier tokens/minute limits |
| `CORS_ALLOWED_ORIGINS`   | backend  | `http://localhost:5173`  | Comma-separated origins allowed to call the API |
| `VITE_API_BASE_URL`      | frontend | `http://localhost:8080`  | Backend URL the UI calls                   |

Never commit real API keys — set them as environment variables (or via your
deployment platform's secret manager).

## Roadmap (from the original design)

The diagram this project started from also calls for a deterministic layer
alongside the LLM:

- **Rule engine** — objective checks (lines changed, auth/migration/Dockerfile/
  k8s files touched, secrets accidentally committed).
- **Static analysis** — language-aware linting/analysis of changed files.
- These get combined into "risk features" that are fed into the LLM prompt
  alongside the raw diff, so the model reasons over both the diff and
  pre-computed signals instead of everything at once.

Not implemented yet in this MVP; the `GroqService` system prompt is the
natural place to wire in that extra context later.
