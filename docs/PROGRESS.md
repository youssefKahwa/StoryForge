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

### M1 (in progress): Claude Code brain (2026-10-17)
- `ClaudeCodeLlmProvider` is the only AI provider (ADR-012): haiku/low effort for light stages, sonnet/medium for
  research (WebSearch + WebFetch only), script and rewrite. Prompt on stdin, system prompt as file, `--safe-mode`.
- Research gate repair: one follow-up call finds second sources; claims still single-sourced become "contested".
- Length control: estimated narrated seconds (words, speed, pauses) feed the critique/rewrite loop; max 1 rewrite.
- Critic judges facts only against dossier claims (it once "corrected" a true date from memory).
- First real run (#33, Mary Celeste, 90 s target): 10 real sources, QA passed, ~51k input / ~52k output tokens,
  most of it in critique + one sonnet rewrite.

## Next: finish M1
- Real 12–16 min run on the `local` profile and check Pro usage per video.
- Trim outline output (9k tokens for a short video), consider `--json-schema` per stage.
- Optional: research fetchers (Wikipedia/MediaWiki, Wikidata, Internet Archive, LoC, Chronicling America) + LLM extraction.
   Gate: ≥ 8 sources.
4. JSON Schemas in `schemas/` + validation for the NVIDIA path; repair loop already exists.
5. `local` profile: real LLMs, mock media.

## Blocked / needs owner
- ~~NVIDIA API key~~ done (in `.env`). Images: NVIDIA FLUX not reachable, so pick another free image source before M3.
- Docker Desktop must be started for the Postgres profile (the `h2` profile works without it).
