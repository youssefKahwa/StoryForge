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

### M3–M5 + Publish Kit (2026-10-18)
- Images: Cloudflare FLUX + Real-ESRGAN 2× upscale; daily quota budget; stable cache keys; reuse fallback.
- Remotion editing: camera motion, crossfades, kinetic text, chapter cards, film look; Shorts (9:16) and thumbnails.
- Sound: synthesized SFX, mood music library with ducking, −14 LUFS.
- Narration: TTS cache, instant atempo length fit, voice pace calibration (af_heart ≈ 151 wpm), keep-awake.
- Publish Kit per run in `storyforge-data/exports/…` + dashboard tab; YouTube upload/veto/Telegram/autopilot (M5)
  built and unit-tested, but not yet exercised against a real channel (needs the owner's Google client).
- Full-length proof: run #65 "Dyatlov Pass", 11.5 min, QA passed, 8 sources, critic 9.0, 3 Shorts, 3 thumbnails.
  Timings plugged in: AI ~10 min, narration 12 min, images ~6 min, render ~40 min, Shorts ~10 min.

### Recheck + quality-of-life upgrades (2026-10-18)
- Fixes: API binds to 127.0.0.1 only; keep-awake helper exits with the API (no orphan PowerShell); unknown enum
  values in stored JSON read as null instead of failing; media-svc starts automatically when a run needs it.
- One command: `scripts/start.ps1` builds the dashboard when its sources changed, and Spring serves it at :8080
  (Gatsby folder pages and `/run?id=` deep links work, so Telegram links open directly).
- Preferences (Settings, stored in DB): default voice/length/format, Shorts on/off and count, burned captions,
  AI upscale, music volume (off/low/normal/high), publish day/time/audience zone. Used by new runs and stages.
- System check card: Claude Code, FFmpeg, media-svc, Remotion, Real-ESRGAN, Cloudflare quota left, music per mood,
  free disk, YouTube, Telegram, each with a fix hint.
- Storage card: free disk, run/export sizes, cleanup of media in completed runs older than N days (exports kept).
- QA warning `audio.music` when a video ends up without background music.
- Tests: 43 passing (new: preferences E2E, system check, storage cleanup).

### Music library (2026-10-19)
- 21 CC BY 4.0 tracks (Kevin MacLeod, incompetech.com), 3 per mood, chosen from the incompetech catalog by mood
  tags (instrumental only). `licenses.json` gives each one type `cc-by-4.0`, so the attribution goes into
  the description. ISRC proof is in `MUSIC_CREDITS.txt`. System check: music OK.
- `scripts/set-cloudflare-token.ps1`: hidden prompt, verifies with Cloudflare, writes `.env`.

### V2: premium look + near-zero interaction (2026-10-19)
- Shot director with visual bible: a distinct, story-matched prompt per image and consistent recurring characters.
- Documentary overlays in Remotion: location/date lower thirds, quote cards, year markers (narration-verified).
- Revise tab: mark a moment, note picture/narration/music/title changes, apply; targeted re-run with caches.
- Quick start (links, topic or nothing), channel brief, AI topic ideas; autopilot fills its own backlog.
- Script prompt 0.3.0: cold open, open loops per chapter, re-hooks every 60–90 s, spoken places/years.
- Tests: 48 passing (new: director overlays E2E + unit, revisions E2E, quick start E2E).

### Real media + talking host (2026-10-20)
- Real photos (Wikimedia Commons) and stock footage (Pexels, optional key) mixed with illustrations, license-filtered,
  relevance-checked, credited on screen and in the description. Live probe: real 1959 Dyatlov tent photo (PD),
  Flannan Isles lighthouse (CC BY-SA), Mary Celeste 1861 painting (PD); a same-name island was correctly rejected.
- Talking logo host with loudness lip-sync at the opening, questions and outro.

### BiPath style, transitions, clips (2026-10-20)
- All text in the BiPath look (Bungee block letters, slate extrusion, evil-eye blues, snow on cards).
- Transitions with synthesized swish/impact sounds timed to the cut (whip, zoom, flash, crossfade).
- Footage from Pixabay (key) and Wikimedia Commons (keyless) since Pexels stopped issuing keys.
- Automatic fair-use trailer clips for film/series/anime stories, chosen by Claude from trailer contact sheets.

## Next
- Owner steps in `docs/OWNER_SETUP.md` (roll Cloudflare token, YouTube client, Telegram, power settings).
- First real YouTube upload test once the Google client exists.
- M6: analytics pulls + retention-based learning; M7: Film-analysis format.

## Blocked / needs owner
- ~~NVIDIA API key~~ done (in `.env`). Images: NVIDIA FLUX not reachable, so pick another free image source before M3.
- See docs/OWNER_SETUP.md. Docker Desktop is optional (the `h2` profile works without it).
