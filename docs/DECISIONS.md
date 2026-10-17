# Decisions (ADRs)

Short records: decision, options, why, date. Newest last.

---

## ADR-001 Zero-cost provider stack (2026-10-17)
**Decision.** Every provider must be free to run. Paid providers stay possible behind the same interfaces, but the
budget cap defaults to **$0** (`storyforge.budget.max-usd-per-video: 0`), so any paid call blocks the run.

| Concern | Free choice | Fallback / notes |
|---|---|---|
| AI brain | Claude Code CLI on the owner's existing **Pro** login (no extra cost) | NVIDIA hosted API (free developer tier) |
| Research sources | Free public APIs fetched by Java (Wikipedia/MediaWiki, Wikidata, Internet Archive, Library of Congress, Chronicling America) | LLM extracts claims; LLM web search only as a fallback |
| TTS | Kokoro-82M (Apache-2.0), local on CPU via media-svc | Piper (local) |
| Alignment | faster-whisper / WhisperX on CPU (int8) | — |
| Images | NVIDIA hosted FLUX.1-schnell (model page says commercial use OK) | Cloudflare Workers AI free daily allocation; Java2D placeholders |
| Motion video | None in v1: stills with camera motion + Remotion graphics | M7 |
| Music/SFX | Local library: YouTube Audio Library, Pixabay, Freesound CC0, Incompetech CC-BY, each with a license JSON | — |
| Render | FFmpeg slideshow now, Remotion (free for individuals) in M3 | — |
| DB | PostgreSQL 16 in Docker Desktop | H2 file DB (`h2` profile) when Docker is off |
| Discovery / publish | YouTube Data API free quota (10k units/day), Wikipedia Pageviews, Wikidata SPARQL | — |
| Notifications | Telegram bot | email |
| Remote veto | Tailscale free plan | — |

**Excluded:** FLUX.1-dev (non-commercial license on its model page), ElevenLabs/Google TTS (paid beyond tiny free tiers).

## ADR-002 LLM routing: Claude Code for writing, NVIDIA for bulk (2026-10-17)
**Options.** (a) Claude Code for everything, (b) NVIDIA for everything, (c) route per stage with fallback.
**Decision.** (c). `LlmRouter` reads a route per stage from `storyforge.llm.routes`, tries providers in order, and falls
back on error. Defaults: `script` and `rewrite` go to Claude Code first (quality matters most, about 2 calls per week).
`select`, `research` extraction, `outline`, `critique` and `package` go to NVIDIA first. A different model family as
critic also gives a genuinely fresh read.
**Token rules (Pro has usage limits):**
- Content-addressed **LLM cache**: SHA-256 of (route, stage, prompt version, tier, system, user, cache salt). Re-runs
  with unchanged inputs cost zero tokens. "Fresh" re-run bumps the run's `cache_salt`.
- Prompts send compact JSON digests, never whole dossiers. Shot timing is done in Java (ADR-006), not by the LLM.
- Claude Code headless flags (verified with `claude --help`, v2.1.287, 2026-10-17): `-p --output-format json
  --model <alias> --tools "" --system-prompt <stage prompt> --json-schema <schema> --strict-mcp-config
  --disable-slash-commands --no-session-persistence --permission-mode dontAsk`. Replacing the default system prompt
  and skipping MCP, skills and CLAUDE.md keeps each call small. `--bare` cannot be used: it requires an API key and
  ignores the Pro OAuth login.
- Unattended auth: logged in via claude.ai (Pro). `claude setup-token` provides a long-lived token if the orchestrator
  ever runs in a container. To verify in M1.
**Sources.** `claude --help` output (v2.1.287, 2026-10-17).
NVIDIA endpoints: `https://integrate.api.nvidia.com/v1/chat/completions` (OpenAI-compatible, `Authorization: Bearer
nvapi-…`). Image: `https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-schnell`
(https://build.nvidia.com/black-forest-labs/flux_1-schnell, read 2026-10-17; governed by the NVIDIA API Trial ToS).
Free-tier limits (about 40 requests/min) come only from secondary sources. Re-verify on build.nvidia.com in M1.

## ADR-003 Spring Boot 4.1.1 instead of 3.x (2026-10-17)
Spring Initializr no longer offers 3.x. Boot 4.1.1 still supports JDK 17 (installed). Consequences: Jackson 3
(`tools.jackson.*`), Hibernate 7, modular starters (`spring-boot-starter-webmvc`, `-flyway`). Jackson 3 defaults
`FAIL_ON_NULL_FOR_PRIMITIVES=true`, which breaks on sloppy LLM JSON, so it is disabled in `application.yml`.

## ADR-004 Dashboard: Gatsby 5 + React 18 + antd 4 + Redux Saga (2026-10-17)
The owner asked for Gatsby. Gatsby 5.16 allows React 18/19. antd 4 relies on `ReactDOM.render` for static
message/modal APIs, so React is **pinned to 18.3.1**. Run detail uses `/run?id=` (static page + client fetch), so no
client-only route plugin is needed. react-intl is pinned to 7.1.14 for React 18 compatibility.

## ADR-005 Run state vs run status (2026-10-17)
The spec's state machine has `FAILED`/`BLOCKED` as states. They are modelled as **`run.status`** (QUEUED, RUNNING,
COMPLETED, FAILED, BLOCKED, CANCELLED) next to **`run.state`** (last milestone reached). Resume then simply continues
at `Stage.after(state)`. The job queue is the `run` table itself, polled by a single worker thread. That is enough
for one video per week, with no broker.

## ADR-006 Deterministic shot planner in Java (2026-10-17)
The spec has Claude create the shot list. Instead, the LLM writes the visual intent per scene (`@visual`, `@onscreen`)
inside the script, and `ShotPlanner` does the timing: 3–6 s shots, cuts on phrase boundaries, at most 3 of a type in a
row, and a pattern interrupt every ≤ 60 s. This is cheaper (zero tokens), testable and exact. The LLM can still refine
prompts in M3 if needed.

## ADR-007 Orchestrator runs on the host in v1 (2026-10-17)
It shells out to the owner's logged-in `claude` CLI and to the host FFmpeg. Compose runs Postgres now; media-svc and
render-svc join in M2/M3. Containerising the orchestrator later needs `claude setup-token`.

## ADR-008 Run data outside OneDrive (2026-10-17)
`storyforge.data-dir` defaults to `${user.home}/storyforge-data` (not synced). Gigabytes of intermediate media must
not sync. Postgres data lives in a Docker named volume.

## ADR-009 Machine constraints (2026-10-17)
Windows 11, 16 GB RAM, **no NVIDIA GPU**, about 42 GB free disk. Local image generation is impossible, so images are
hosted (ADR-001). TTS and alignment run on CPU (Kokoro and whisper int8 are fine on CPU for one video per week).
A retention policy for heavy intermediates matters early because of the disk.

## ADR-010 Default publish slot (2026-10-17)
Saturday 14:00 America/New_York (English-speaking audience). Upload happens at the end of the pipeline; publish time
is the first slot after the 24 h veto window. The Learn stage will replace this with the learned best slot (M6).
