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

## How it start in Docker 

## Docker (backend and frontend as separate images)


**Quickest path — Docker Compose:**
 https://console.groq.com/keys  go to that link, and CREATE API KEY then give name after that one time visible code copy and paste 
 export GROQ_API_KEY=gsk_...yourkey...  that command


```bash
export GROQ_API_KEY=gsk_...yourkey...   # if not already exported, e.g. in ~/.bash_profile


docker compose up --build
```

Open `http://localhost:8081`. `Ctrl+C` to stop, or `docker compose down`.

**Manual, one at a time** (useful if you want to rebuild/restart just one
side, or understand what Compose is doing under the hood):

**Backend:**

```bash
export GROQ_API_KEY=gsk_...yourkey...      # one-time, e.g. in ~/.
cd backend
docker build -t ai-pr-reviewer-backend .
docker run -p 8080:8080 \
  -e GROQ_API_KEY=gsk_...yourkey... \
  -e CORS_ALLOWED_ORIGINS=http://localhost:8081 \
  ai-pr-reviewer-backend
```

Anyone else running this project just exports their own key in their own
shell first — nothing secret ever lives in a file in the repo.

**Frontend:**

```bash
cd frontend
docker build -t ai-pr-reviewer-frontend \
  --build-arg VITE_API_BASE_URL=http://localhost:8080 .
docker run -p 8081:80 ai-pr-reviewer-frontend
```

Open `http://localhost:8081`. Note the backend's `CORS_ALLOWED_ORIGINS` above


## Prerequisites

- Java 17+
- Maven 3.9+ (or use the included setup with your IDE)
- Node.js 18+
- A free Groq API key from https://console.groq.com/keys

  requests/hour unauthenticated rate limit — needed at all for private repos.

## Backend setup

```bash
cd backend
export GROQ_API_KEY=gsk_...
mvn spring-boot:run
```

```
## Frontend setup

```bash
cd frontend
on :8080
npm install
npm run dev
```

Open `http://localhost:5173`, paste a PR URL, and click "Review PR".



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
