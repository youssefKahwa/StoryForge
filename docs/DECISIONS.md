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

## ADR-011 NVIDIA free key: what actually works (verified 2026-10-17)
Key created (expires 2027-10-17), stored only in `.env` (git-ignored). `GET /v1/models` lists 81 models, but **being
listed is not the same as being callable**:
- `nvidia/nemotron-3-super-120b-a12b`: works, ~2 s for a small JSON reply. It is a reasoning model (84 of 120
  completion tokens were reasoning), so `max_tokens` needs generous headroom or the JSON gets truncated.
- `moonshotai/kimi-k2.6`: 404 "Function not found for account" (not enabled for this key).
- `deepseek-ai/deepseek-v4.1-flash`: no response within 90 s.
- `flux.1-schnell` image endpoint: no response within 280 s (two tries). **Not usable for now.** The M3 image provider
  needs another free source (candidates: Cloudflare Workers AI free tier, Pollinations). Re-test before M3.
**Consequence for M1:** `NvidiaLlmProvider` takes an ordered model list per tier, with a short connect timeout, and
skips models that return 404 or time out. Tier `fast` = nemotron-3-super first.

## ADR-012 Claude Code is the only AI provider (2026-10-17, owner request)
Supersedes the routing split in ADR-002. Every LLM stage routes to `claude-code` (`default-route: [claude-code]`,
and the `local` profile pins `llm.only: claude-code`). NVIDIA stays out of the route; the key in `.env` is unused.
- Models by prompt tier: `fast` = haiku (select, outline, critique, package), `high` = sonnet (research, script,
  rewrite).
- Least privilege: `--tools ""` for every stage except research, which gets `--tools WebSearch,WebFetch
  --allowedTools WebSearch,WebFetch`. Always `--safe-mode --strict-mcp-config --disable-slash-commands
  --no-session-persistence --permission-mode dontAsk`, with the system prompt passed as a file (no Windows quoting
  issues) and the user prompt on stdin (no command-line length limit).
- Measured 2026-10-17 (v2.1.287, Pro login): a minimal haiku call costs ~600 input tokens and ~1.7 s. On a
  subscription the cost is recorded as $0; the notional API price is ignored by the cost guard.

## ADR-013 Local voice: Kokoro (kokoro-onnx) + faster-whisper in media-svc (2026-10-18)
- `media-svc/` (FastAPI, Python 3.12) runs natively on 127.0.0.1:8090. Its venv lives in
  `%USERPROFILE%\.storyforge\media-venv` and its models in `%USERPROFILE%\storyforge-data\models`, both outside OneDrive.
  Start it with `scripts/media-svc.ps1` (`-Install` the first time).
- TTS: `kokoro-onnx` (MIT) with the Kokoro-82M v1.0 model (Apache-2.0), CPU, 24 kHz mono.
  Docs: https://github.com/thewh1teagle/kokoro-onnx (examples/save.py), voices graded at
  https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md (read 2026-10-18). Default voice `af_heart` (grade A);
  `am_michael` is the best-graded male option (C+). Measured: ~133 wpm at speed 1.0, ~1.5-2x real time on this CPU,
  so `local` sets `video.words-per-minute: 135`.
- Alignment: `faster-whisper` 1.2.1 `base.en`, int8 on CPU (https://github.com/SYSTRAN/faster-whisper, 2026-10-18).
  Audio is decoded with soundfile and resampled to 16 kHz in numpy, because the current PyAV rejects faster-whisper's
  `metadata_errors` argument. Script words are matched to heard words with difflib, so captions keep the script's
  spelling and the timing comes from Whisper. The word error rate per scene drives the mispronunciation check: above
  `narration.max-wer` (0.15), the scene is re-synthesized at 0.92x speed and the better take is kept. Measured WER
  0.04-0.08 on a test sentence (proper nouns are the usual misses).

## ADR-014 Inspiration videos: read transcripts, firewall the wording (2026-10-18, owner request)
The owner gives 1-3 YouTube links (often Darija/Arabic). Titles alone miss the details, so the app reads transcripts.
- `media-svc /transcript` uses yt-dlp (2026.08.19) metadata + caption tracks only (no video download). It prefers
  manual captions in the video's language, then YouTube's original-language ASR (`<lang>-orig`). Without captions it
  downloads the audio only, transcribes it with multilingual faster-whisper `small`, and deletes the audio. Pasting a
  transcript is always possible. YouTube's ToS restrict automated access; this is personal research use and the owner
  was told.
- Firewall: the `inspire` prompt turns transcripts into English ideas + leads (own words). Discover keeps only the
  ideas file in the run; research must confirm leads in independent sources and never cite the video; the script
  writer never sees transcripts. QA `legal.originality` (critical) compares the script with the transcripts: the
  longest shared word run must be < 12 words and the share of shared 6-word phrases must be ≤ 5%.

## ADR-015 Images: Cloudflare Workers AI FLUX.1-schnell (2026-10-18)
- Pollinations (tested): anonymous output has a watermark, the API now needs a key, and credits and image license are
  not documented (https://raw.githubusercontent.com/pollinations/pollinations/refs/heads/main/APIDOCS.md, 2026-10-18).
  Rejected.
- Cloudflare: `POST https://api.cloudflare.com/client/v4/accounts/{account}/ai/run/@cf/black-forest-labs/flux-1-schnell`,
  `{prompt, steps<=8, seed}` → base64 `image` (https://developers.cloudflare.com/workers-ai/models/flux-1-schnell/).
  Free 10,000 neurons/day; schnell = 4.80 per 512² tile + 9.60 per step, about 58 neurons per 1024² image at 4 steps,
  so about 170 images/day (https://developers.cloudflare.com/workers-ai/platform/pricing/, 2026-10-18).
  FLUX.1-schnell is Apache-2.0. Needs a free Cloudflare account ID + API token (owner input).
- Verified 2026-10-18 with the owner's token: 1024×1024 JPEG in ~1.4 s, no watermark. The model **rejects a `seed`
  field** (error 5006), despite the docs example, so consistency relies on the Style Bible prompt plus a content-hash
  image cache (`storyforge-data/cache/images`); re-runs cost no quota. Images are cover-cropped to 1920×1080 PNG on
  save, because FFmpeg's concat input cannot mix codecs or frame sizes. A failed image falls back to a placeholder card
  and QA raises the `content.real_images` warning.

## ADR-016 Remotion editing (render-svc) (2026-10-18)
- Remotion 4.0.536. License (LICENSE.md, read 2026-10-18): free for "an individual", including use "commercially
  for the purpose of creating videos and images". Docs: https://www.remotion.dev/docs/ssr-node and /docs/bundle.
- Source lives in `render-svc/`; it is installed and run from `%USERPROFILE%\.storyforge\render-svc` (outside
  OneDrive, 277 MB of node_modules). `RemotionRenderer` syncs changed sources before each render, then runs
  `node render.mjs <edl> <runDir> <out>`. render.mjs serves the run folder on a private 127.0.0.1 port (Range
  support for audio) instead of copying gigabytes into Remotion's public dir.
- LongForm composition: Ken Burns motion on every still, 10-frame crossfades, kinetic key phrases (Cinzel) over a
  blurred previous image, chapter cards from chapter 2 on (the opening title covers chapter 1), vignette plus a
  shifting pre-generated grain tile, 0.4 s fade-out (below the 0.5 s black-frame QA threshold), optional burned
  captions (off; YouTube gets captions.srt).
- Speed on the i7-1255U: 97 s of video renders in 401 s at concurrency 8 (≈4× real time), so about 60 minutes for a
  15-minute video. The per-frame SVG turbulence grain was the main cost (587 s before).
