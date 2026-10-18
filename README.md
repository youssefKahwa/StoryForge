# StoryForge

A self-hosted studio that researches, writes, narrates, illustrates, edits, checks and publishes one long-form YouTube
video per week. Everything runs on free providers (see `docs/DECISIONS.md`, ADR-001).

- Spec: `docs/SPEC.md` · Progress: `docs/PROGRESS.md` · Decisions: `docs/DECISIONS.md`

## Layout
```
orchestrator/   Spring Boot 4 (JDK 17): state machine, stage workers, providers, QA gate, REST API
dashboard/      Gatsby 5 + React 18 + antd 4 + Redux Saga + react-intl
prompts/        versioned prompt templates (front matter: version, tier)
config/formats/ format plugins: beat sheet + style bible
docker-compose.yml  PostgreSQL 16
```
Run artifacts go to `%USERPROFILE%\storyforge-data\runs\{runId}\{stage}\` (outside OneDrive).

## Prerequisites
JDK 17, Node 22, FFmpeg on PATH, Docker Desktop (optional: the `h2` profile works without it).

## Weekly use
1. `.\scripts\start.ps1` (charger plugged in). It builds the dashboard if needed, starts everything and opens
   http://localhost:8080. Check **Settings → System check** is green.
2. **New video**: paste 1–3 YouTube links → pick an angle and a voice → Start.
3. After 1–2 hours, open the run → **Publish kit** tab (or the folder in `%USERPROFILE%\storyforge-data\exports`).

One-time setup: `docs/OWNER_SETUP.md`.

## Commands
| What | Command |
|---|---|
| Weekly use: API + dashboard + media-svc, one window (H2) | `.\scripts\start.ps1` (`-Docker` for Postgres, `-Rebuild`, `-Mode mock`) |
| One-command mock demo (no keys, no Docker) | `.\scripts\e2e-mock.ps1` |
| All tests | `cd orchestrator; .\mvnw.cmd test` |
| Dev with hot reload: API + `gatsby develop`, Postgres in Docker | `.\scripts\dev.ps1` |
| Dev without Docker (H2 file DB) | `.\scripts\dev.ps1 -NoDocker` |
| Dashboard only | `cd dashboard; npm run develop` (http://localhost:8000) |
| API only | `cd orchestrator; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mock,h2"` (http://localhost:8080) |

## Profiles
- `mock`: canned LLM output, sine-wave narration, placeholder images, FFmpeg slideshow, no upload (about 70 s video).
- `local` (M1+): Claude Code + NVIDIA free API for text, local TTS/alignment, no upload.
- `h2`: add to any profile to use an H2 file DB instead of Postgres.

## API
`GET/PUT /api/preferences` · `POST /api/preferences/reset` · `GET /api/system/check` ·
`GET /api/system/storage?olderThanDays=14` · `POST /api/system/cleanup?olderThanDays=14` ·
`GET/POST /api/runs` · `GET /api/runs/{id}` · `POST /api/runs/{id}/cancel|resume` ·
`POST /api/runs/{id}/stages/{stage}/rerun?fresh=false` · `GET /api/runs/{id}/files/{stage}/{name}` ·
`GET/POST/DELETE /api/seed-topics` · `GET /api/settings` · `PUT /api/settings/kill-switch`
