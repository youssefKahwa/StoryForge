# Progress

## Done
### M0: Skeleton + mock end-to-end (2026-10-17)
- Spring Boot 4.1 orchestrator: Flyway schema (`seed_topic`, `run`, `stage_execution`, `cost_entry`, `app_setting`),
  17-stage state machine, DB-backed queue with a single worker, kill switch, cost guard ($0 cap), resume, cancel,
  re-run from any stage (LLM cache keeps re-runs free).
- Provider interfaces + mocks: `LlmProvider` (router with per-stage fallback + cache), `TtsProvider`,
  `AlignmentProvider`, `ImageProvider`, `RenderProvider` (FFmpeg slideshow), `PublishProvider`.
- Real logic already in place: StoryForge Markup parser, shot planner, caption/SRT builder, EDL builder, FFmpeg
  loudness normalisation (−14 LUFS), QA gate (technical, content, factual, legal), title scorer, description with
  chapters + sources + AI disclosure, schedule slot, learning plan.
- Versioned prompt templates in `prompts/`, format plugins in `config/formats/`.
- Gatsby dashboard: run list with stage chips, run detail (executions, video player, thumbnail, titles,
  description, QA table, script, files), re-run/resume/cancel, seed topics, settings + kill switch.
- Tests: 19 unit tests + 2 end-to-end mock pipeline tests.
- Demo: `scripts/e2e-mock.ps1` (about 70 s placeholder 1080p MP4 + QA report, zero API keys).

## Next: M1, the brain (real AI, free)
1. `ClaudeCodeLlmProvider` (ProcessBuilder, flags from ADR-002, JSON schema per stage, timeout, usage parsing).
   Measure the real token footprint of a minimal call.
2. `NvidiaLlmProvider` (OpenAI-compatible HTTP). Owner creates a free key at build.nvidia.com and puts it in `.env`.
3. Research fetchers (Wikipedia/MediaWiki, Wikidata, Internet Archive, LoC, Chronicling America) + LLM extraction.
   Gate: ≥ 8 sources.
4. JSON Schemas in `schemas/` + validation for the NVIDIA path; repair loop already exists.
5. `local` profile: real LLMs, mock media.

## Blocked / needs owner
- NVIDIA API key (free) before M1 step 2.
- Docker Desktop must be started for the Postgres profile (the `h2` profile works without it).
