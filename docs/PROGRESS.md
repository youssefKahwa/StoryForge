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

### M2: Voice (2026-10-18)
- `media-svc` (FastAPI): Kokoro TTS + faster-whisper alignment on CPU, venv and models outside OneDrive (ADR-013).
- `KokoroTtsProvider`, `WhisperAlignmentProvider`, `AlignmentAssembler`; `local` profile uses them.
- Mispronunciation check: per-scene WER after synthesis; above 0.15 the scene is re-synthesized at 0.92x and the
  better take is kept. Word timings reuse the verification pass (no second transcription).
- Length fit: critique trims via a cheap haiku pass when still off; narration re-synthesizes at up to ±12% speed.
- Loudness: gain + peak limiter loop (2-pass loudnorm could not lift peaky TTS speech without falling back to dynamic).
- Run #33 (Mary Celeste, 90 s target) completed with real voice: 97.3 s, −14.22 LUFS, −3.3 dBTP, QA passed.

## Next
- M3 picture: pick a free image source (NVIDIA FLUX did not respond), Remotion assembly with camera motion.
- One full-length (12–16 min) `local` run to measure Pro usage and CPU time per video.
- Trim outline output tokens; consider `--json-schema` per stage.

## Blocked / needs owner
- ~~NVIDIA API key~~ done (in `.env`). Images: NVIDIA FLUX not reachable, so pick another free image source before M3.
- Docker Desktop must be started for the Postgres profile (the `h2` profile works without it).
