# StoryForge — Autonomous Long-Form Video Studio

> Codename: **StoryForge** (rename freely).
> A private, self-hosted machine that researches, writes, narrates, illustrates, edits, publishes and learns, producing **one excellent long-form YouTube video per week** with almost no human work.

> Owner amendments (2026-10-17): everything must be **free** (no paid APIs). AI work uses the owner's Claude Code
> login sparingly plus the free NVIDIA API (build.nvidia.com). Dashboard is **Gatsby + React**.
> See `docs/DECISIONS.md` for how each section maps onto free providers.

---

## 0. Read this first: instructions for Claude Code

You are the **lead engineer** of this project. I am the owner and supervisor. This document describes the *perfect* version of the machine. We build it step by step, starting with v1, and we solve problems together as they appear.

**Working rules**

1. Read this entire document before writing any code.
2. Then ask me **up to 5 questions** whose answers would change the architecture. After my answers, propose:
   - a short architecture summary,
   - the repository structure,
   - the milestone plan (Section 19), adjusted if needed.

   Wait for my "go" before coding.
3. Build **milestone by milestone**. Each milestone must end in a runnable state, with tests and a one-line demo command.
4. Keep two living documents:
   - `docs/PROGRESS.md`: done / next / blocked
   - `docs/DECISIONS.md`: short ADRs (decision, options, why, date)
5. **Never guess external APIs.** Before implementing any provider (TTS, image, video, YouTube, etc.), read its current official documentation. Record the doc URL and date in `DECISIONS.md`. Run `claude --help` to confirm current CLI flags before wiring the Claude Code runner.
6. Every external provider sits behind an **interface with a `Mock` implementation**. The full pipeline must run end-to-end in the `mock` profile **with zero API keys**.
7. Secrets come only from environment variables (`.env`, never committed).
8. **Code style (mine):**
   - Spring Boot + JPA, thin controllers, a service layer.
   - One DTO per file, each with a static `from()` factory method.
   - Externalized config, clean code, no unnecessary comments.
   - Flyway migrations.
9. When you are blocked:
   - explain the options and tradeoffs,
   - pick the most sensible default,
   - log it in `DECISIONS.md`,
   - continue.
10. **Safety:** when the app invokes Claude Code in headless mode, always pass a **restricted `--allowedTools` list** per stage (Section 6). Never give it unrestricted shell, delete, or credential access.

---

## 1. Vision

StoryForge is a studio in a box. Every week it:

1. **Discovers** what audiences want to watch.
2. **Selects** the single best story or film to cover.
3. **Researches** it deeply, from real and independent sources.
4. **Writes** an original, gripping script engineered for retention.
5. **Narrates** it with a consistent signature voice.
6. **Illustrates** it with a consistent visual style, frame-matched to every sentence.
7. **Scores** it with music and sound design.
8. **Edits** everything into a polished long-form video.
9. **Quality-checks** it (technical, factual and legal).
10. **Packages** it: titles, thumbnail, description, chapters, Shorts.
11. **Publishes** it on schedule.
12. **Learns** from the analytics and improves itself for next week.

**Owner effort target:** ≤ 30 minutes per week (review the dashboard; optionally veto before publishing).

**North-star metrics**
- Average view duration %
- Click-through rate
- Returning viewers
- Subscribers per video

**Operational metrics**
- Pipeline success rate
- Cost per video
- Human minutes per week

---

## 2. Channel formats (plugins)

A **Format** is a plugin. It defines a beat sheet, research rules, visual rules, packaging rules and guardrails. The engine is format-agnostic.

### 2.1 Format A: Great Stories
True stories, historical events, mysteries, legends, survival, heists, rise-and-fall stories, public-domain tales (e.g. *One Thousand and One Nights* from public-domain translations).

**Beat sheet (target 14–20 min):**
| Beat | Time | Purpose |
|---|---|---|
| Cold open | 0:00–0:30 | Start *in the middle of the most intense moment*. State the stakes. No "welcome back". |
| Promise | 0:30–0:50 | What the viewer will understand by the end; open the main loop |
| Context | ~1–3 min | Who, where, when, told through a character, not a lecture |
| Escalation ×3 | middle | Intention → obstacle → consequence, each raising the stakes |
| Midpoint twist | ~50% | A revelation that reframes everything |
| Dark moment | ~70–80% | Things look lost |
| Climax | ~85–90% | The main loop pays off |
| Resolution + reflection | final 60–90 s | Meaning, aftermath, one-line tease of the next video |

### 2.2 Format B: Film Analysis (with recap)
Essays about films and series: themes, character arcs, symbolism, structure, why a film works, hidden details, endings explained.

**Beat sheet (target 12–18 min):**
| Beat | Purpose |
|---|---|
| Hook question / thesis | A provocative question the video answers |
| Spoiler notice + quick context | Release, director, why it matters, in one breath |
| Recap **in our own words** | **Max ~30% of runtime**, summarized, never scene-by-scene |
| Analysis pillars ×3–5 | Theme, character arc, symbolism, craft (described, not shown), score/sound (described) |
| Counter-reading | The strongest alternative interpretation |
| Verdict / thesis payoff | Answer the hook question |
| Tease | The next video |

---

## 3. Guardrails (non-negotiable, enforced as code in the Legal QA gate)

1. **Originality**
   - Other creators' videos may be used **only as topic signals**: title, view counts, topic.
   - Never ingest their transcripts into our scripts. Never reuse their footage, audio or structure.
2. **Film analysis**
   - No film footage, trailers, stills, official posters or soundtrack.
   - No generated images depicting **real actors' likenesses** or **copyrighted characters**.
   - Visuals are original: symbolic, atmospheric, diagrams, typography.
   - Never recreate iconic shots.
   - The recap is in our own words and ≤ 30% of the runtime. Commentary and analysis must dominate.
3. **Public-domain claims**
   - Must carry an evidence record: source URL, publication date, jurisdiction note.
   - Modern translations, restorations and new scores are **not** public domain by default.
4. **Music and SFX**
   - Only from the local licensed library, with license metadata.
   - Unknown license → **block publishing**.
5. **Facts**
   - Every factual claim in the script maps to ≥ 1 source in the dossier. Key claims need ≥ 2 independent sources.
   - Contested claims are narrated as contested ("according to…").
   - No fabricated quotes.
6. **Real people**
   - No defamation.
   - Living people are treated with care.
   - No generated realistic depictions of real people.
7. **Disclosure**
   - Set YouTube's altered/synthetic content disclosure when visuals are realistic.
8. **Budget**
   - Hard cost cap per video (Section 13).

---

## 4. Architecture

```
                 ┌───────────────────────── Dashboard (React) ─────────────────────────┐
                 │ runs · previews · veto · playbook · topics · costs · kill switch    │
                 └──────────────────────────────┬──────────────────────────────────────┘
                                                │ REST
┌──────────┐   ┌────────────────────────────────▼───────────────────────────────────┐
│Scheduler │──▶│                 Orchestrator (Spring Boot)                          │
└──────────┘   │ run state machine · stage workers · provider adapters · cost guard │
               └──┬───────────┬──────────────┬───────────────┬───────────────┬──────┘
                  │           │              │               │               │
          Claude Code CLI  media-svc     render-svc      providers       PostgreSQL
          (headless -p)    (Python)      (Node+Remotion)  (TTS, image,    + artifact store
                           alignment,    16:9, 9:16,      video, YouTube) /data/runs/{runId}
                           local TTS,    thumbnails
                           audio QA
```

**Tech stack**
| Concern | Choice |
|---|---|
| Orchestrator | Spring Boot 3, JDK 17, JPA, Flyway, PostgreSQL |
| Job execution | DB-backed job queue in v1 (one video/week doesn't need a broker); RabbitMQ optional later |
| Scheduling | Spring `@Scheduled` (weekly run + analytics pulls) |
| AI brain | Claude Code CLI in headless mode, invoked via `ProcessBuilder` |
| Media sidecar | Python 3 + FastAPI: forced alignment (WhisperX), optional local TTS, audio analysis |
| Rendering | Node + **Remotion** (React-based programmatic video) |
| Media utilities | FFmpeg (loudness, concat, black/freeze detection, transcode) |
| Dashboard | React, antd 4, Redux Saga (onSuccess/onFail callbacks), centralized `ApiRoutes.js`, react-intl |
| Deployment | Docker Compose (orchestrator, media-svc, render-svc, postgres, dashboard) |

---

## 5. The pipeline (weekly run)

**Run state machine:**
`CREATED → DISCOVERED → SELECTED → RESEARCHED → OUTLINED → SCRIPTED → CRITIQUED → NARRATED → ALIGNED → VISUAL_PLANNED → ASSETS_READY → AUDIO_MIXED → ASSEMBLED → QA_PASSED → PACKAGED → SCHEDULED → PUBLISHED → LEARNING`
- Any stage can move the run to `FAILED` or `BLOCKED`, with a reason.
- Stages are **idempotent and resumable**: re-running a stage reuses its cached artifacts unless they're invalidated.
- Every stage writes:
  - its artifact to `/data/runs/{runId}/{stage}/`,
  - a row in `stage_execution` (status, timings, cost, prompt version, error).

### S1. Discover
**Inputs:**
- **YouTube Data API:**
  - Search by seed keywords in EN and AR.
  - For each result, compute an **outlier score**: video views ÷ median views of that channel's last 20 uploads, weighted by recency.
  - Small Arabic or Darija channels are signals only.
- **Wikipedia Pageviews API:** topics with spikes or steady high interest.
- **Wikidata (SPARQL):** upcoming anniversaries in the next 3–6 weeks (film releases, historical events). Wikidata data is CC0.
- **Seed backlog:** my manual list in the DB, which always gets considered.
- **The Playbook (S16):** which topic families performed well before.

**Output:** 30–50 `topic_candidate` rows.

### S2. Select
- Claude scores each candidate on a rubric (0–10 each):
  - demand
  - English-language gap
  - story strength (stakes, conflict, twist, emotion)
  - visualizability
  - research availability
  - legal safety
  - channel fit
  - freshness
- **Weighted score** with reasons. Weights live in the Playbook.
- **De-duplication:** reject topics too similar to past videos (embedding similarity).
- **Output:** a `topic_brief.json` for the winner plus 2 backups (angle, core promise, target duration, title seeds, format).

### S3. Research
- Claude Code with web search and fetch builds a **dossier**:
  - timeline, characters, places
  - key facts, each with a source reference
  - contested points
  - a "**wow facts**" list (fuel for hooks and re-hooks)
  - visual reference notes (text descriptions only, never copied images)
- **Source credibility tiers:** primary, academic, reputable press, encyclopedic, other.
- **Gate:** minimum source count, coverage of every beat, no single-source key claims.
- **Output:** `dossier.json` plus `sources.json`.

### S4. Outline
- Fill the Format's beat sheet with dossier material, and place the **re-hooks** (every 90–150 s) and **chapters**.
- **Output:** `outline.json` with the duration budget per beat (word budget at ~150 words/min).

### S5. Script
- Write narration in **StoryForge Markup** (Section 7), scene by scene.
- **Each scene contains:** id, beat, narration, tone, pace, emotion, visual intent, SFX cues, music mood, on-screen text.
- **Output:** `script.sfm.md` plus `scenes.json`.

### S6. Critique & rewrite (separate "Critic" call, fresh context)
- **Rubric (1–10):**
  - hook in the first 15 s
  - clarity
  - stakes
  - curiosity gaps
  - pacing variety
  - specificity
  - originality of angle
  - factual traceability
  - ending payoff
- **Cold-read test:** the critic summarizes what the viewer knows at each minute and flags confusion or dead zones.
- **Pass:** average ≥ 8 and hook ≥ 9. Otherwise rewrite (max 3 loops), then take the best version and flag it.
- **Output:** `critique.json` plus the final `script.sfm.md`.

### S7. Narration
- Use the `TtsProvider` interface. Synthesize per scene with one consistent narrator voice, then normalize.
- **Providers (switch via config):** ElevenLabs, Google Cloud TTS, local open-source (e.g. Chatterbox/Kokoro via media-svc).
- **Option:** clone **my own voice** (my consent, my voice) for a human, recognizable identity.
- **Output:** a WAV per scene plus a merged narration track.

### S8. Alignment
- Use forced alignment (WhisperX in media-svc) to get **word-level timestamps**.
- **Mispronunciation check:**
  - Compare the aligned transcript against the script.
  - If a segment's word error rate exceeds a threshold, re-synthesize it with phonetic hints.
- **Output:** `alignment.json`. This is the master clock for editing.

### S9. Visual plan (shot list)
- Claude creates a **shot list** aligned to word timestamps, using the shot types from the Visual Grammar (Section 8).
- **Rules:**
  - a typical shot lasts 3–6 s
  - cut on phrase boundaries
  - never more than 3 consecutive shots of the same type
  - a pattern interrupt (map, kinetic text, SFX hit, music change) at least every ~60 s
- **Each shot has:** type, start/end, prompt (built from the Style Bible), motion, on-screen text, data (for maps and timelines).
- **Output:** `shotlist.json`.

### S10. Asset generation
- **AI stills:** use the `ImageProvider` interface, with Style Bible prompts, character reference sheets and fixed seeds for consistency.
- **Hero motion shots:** use the `VideoProvider` interface (image-to-video), budget-limited to ~20–30% of the runtime. The rest is stills with camera motion.
- **Maps, timelines, kinetic typography, charts:** built by **Remotion components** from data. They're original and cheap.
- **Archival public-domain media:** only with an evidence record (Guardrail 3).
- **Vision QA:** Claude reviews every image against its prompt, the Style Bible and the guardrails:
  - text artifacts
  - anatomy errors
  - inconsistent characters
  - real-person likeness
  - copyrighted characters

  Regenerate failures (max N tries), then fall back to a safe shot type.
- **Output:** `/assets/*` plus `assets.json` (with provider, prompt, seed, cost and license for each asset).

### S11. Music & sound design
- **Music:** choose tracks from the local licensed library by the **mood arc** of the beats, and cut them to beat boundaries.
- **SFX:** place them at script cues (whoosh on transitions, low hits on reveals, ambience per location).
- **Mix:**
  - duck the music under the voice
  - integrated loudness −14 LUFS
  - true peak ≤ −1 dBTP
  - no clipping
- **Output:** `mix.wav` plus `audio_qa.json`.

### S12. Assembly (Remotion)
- **Edit Decision List** (`edl.json`): the single contract between Java and Remotion. It contains shots, timings, transitions, text layers, captions, chapter cards, audio tracks and the color LUT.
- **Compositions:**
  - `LongForm16x9`: 1080p30, optional 4K
  - `Short9x16`
  - `Thumbnail`: a still
- **Craft details:**
  - subtle camera motion on every still
  - consistent color grade
  - kinetic captions for key phrases only
  - an intro sting ≤ 3 s, or none
  - an outro ≤ 20 s with a tease of the next video
- **Output:** `final_16x9.mp4` plus `captions.srt`.

### S13. QA gate (automated, blocks publishing on criticals)
- **Technical:**
  - resolution and fps
  - black frames and freezes (FFmpeg `blackdetect`, `freezedetect`)
  - silence gaps
  - loudness and true peak
  - A/V sync
  - caption sync
- **Content:**
  - every scene rendered
  - shot coverage complete
  - duration within ±10% of target
- **Factual:** all claims traceable to the dossier, no unresolved critic flags.
- **Legal:**
  - every asset has a license or provenance record
  - no film footage or official artwork
  - music licenses present
  - disclosure flag computed
- **Output:** `qa_report.json` plus an HTML report on the dashboard.

### S14. Packaging
- **Titles:** generate 10, score them (curiosity, clarity, ≤ 60 chars, keyword), keep the top 3.
- **Thumbnail:**
  - Generate 6 concepts and render them (AI background + Remotion text layer).
  - Claude's vision model scores them: readable at 160 px wide, ≤ 4 words, one focal subject, strong contrast, emotion.
  - Pick 1 plus 2 alternates.
- **Description:**
  - hook line
  - chapters (from the outline and timestamps)
  - sources list
  - AI disclosure line
- **Tags, pinned comment** (an engagement question), **end-screen plan**.
- **Shorts:**
  - Pick 3–5 self-contained moments, each with a hook and a payoff.
  - Re-render them in 9:16 with captions.
  - End each one with "full story on the channel".

### S15. Publish
- Upload via the YouTube Data API as **private**, with captions, thumbnail, chapters and disclosure.
- Schedule the best publish slot (learned from analytics).
- **Veto window:** notify me with a preview link. If I don't veto within 24 h, it goes public automatically.
- Schedule the Shorts over the following days, each linking to the long video.

### S16. Learn (the "smart" part)
- Pull YouTube Analytics at **+48 h, +7 d, +28 d**: impressions, CTR, average view duration, **audience retention curve**, traffic sources, subscribers gained.
- **Per-scene retention attribution:**
  - Map the retention curve onto the EDL timestamps.
  - Every drop-off or spike is linked to a specific scene, shot type, beat and line.
- **Update the Playbook:** versioned rules with evidence and confidence. Examples:
  - "consequence-first hooks beat question hooks"
  - "map shots reduce drop-off in context beats"
- **Experiments:** change **one variable per week** (hook style, thumbnail style, pacing). Measure it, then keep or revert.
- **Weekly report** to me: what happened, what was learned, what changes next week.

---

## 6. Claude Code integration (the brain)

**`ClaudeCodeRunner`** (Java service):
- **Invocation:** `ProcessBuilder` with `claude -p` and `--output-format json`, plus per-stage flags: allowed tools, system prompt file, max turns. Confirm the exact flags with `claude --help`.
- **Run environment:** a per-run working directory `/data/runs/{runId}/claude/`, timeouts, captured stdout/stderr.
- **Output handling:**
  - Parse the result.
  - **Validate it against a JSON Schema** per stage.
  - If invalid, run a "repair" re-prompt (max 2), then fail the stage.
- **Logging:** duration, usage and cost if reported, prompt version.
- **Prompts** live in `/prompts/{stage}.md` as versioned templates with variables. Each artifact stores the prompt version, so the Learning stage can compare versions.

**Allowed tools per stage (principle of least privilege):**
| Stage | Allowed |
|---|---|
| Select, Outline, Script, Critique, Packaging text | No tools (pure reasoning on provided JSON) |
| Research | Web search, web fetch, read/write inside the run folder only |
| Vision QA, thumbnail scoring | Read (image files in the run folder) |
| Never | Unrestricted shell, delete, network tools outside the list, access to `.env` |

**Auth note:** headless mode must be authenticated non-interactively (API key or a supported login method). Verify this in M1 and document it.

---

## 7. StoryForge Markup (narration format)

This extends my earlier TTS markdown idea. It's parsed by Java and converted to provider-specific SSML or parameters.

```
## SCENE 07 | beat: escalation_2 | duration_target: 38s
[VOICE: narrator_main] [TONE: tense] [SPEED: 0.95] [SILENCE_BEFORE: 300ms]
By sunrise, the caravan had lost two men — and the desert was only getting started.
[PAUSE: 600ms]
[EMPHASIS] Nobody [/EMPHASIS] in Marrakech expected them to come back.
[SILENCE_AFTER: 400ms]
@visual: wide shot, endless dunes at dawn, tiny caravan silhouette, dust in the wind
@sfx: wind_gust_low @ "desert"
@music: mood=dread, intensity=0.6
@onscreen: "Day 3 — 2 men lost"
```

---

## 8. Style Bible & Visual Grammar

**Narrator persona:**
- calm, confident, curious
- short sentences at tense moments, longer ones in reflection
- signature opening and closing lines (defined in config)

**Visual style (per format, in config):**
- art direction (e.g. painterly cinematic realism for stories; symbolic minimal graphics for film analysis)
- palette
- lighting
- typography (2 fonts maximum)
- grain/LUT
- character reference sheets for recurring characters within a video

**Shot types:**
| Type | Use |
|---|---|
| `establishing` | New place or time; wide, slow push-in |
| `character` | Introduce or react; medium or close |
| `detail` | Object or clue; macro, slow zoom |
| `motion_hero` | Peak moments only (AI video) |
| `map` | Journeys, geography, context (Remotion) |
| `timeline` | Dates, sequences (Remotion) |
| `kinetic_text` | Key quote, number, reveal (Remotion) |
| `chart` | Data comparisons (Remotion) |
| `archival_pd` | Public-domain media with an evidence record |
| `symbolic` | Abstract imagery for themes (especially Format B) |

---

## 9. Retention engineering: Playbook v0 (initial rules, then learned)

1. Hook within the first 15 s: a scene or consequence first. Never "welcome back".
2. State the promise and the stakes by 0:30.
3. Open a new curiosity loop every 90–150 s, and close the loops in reverse order.
4. Cause-and-effect chain: every scene must make the next one necessary.
5. A visual change every 3–6 s, and a pattern interrupt at least every ~60 s.
6. No scene longer than 45 s without new information.
7. Specific beats generic: names, numbers, places, sensory details.
8. End fast: payoff, one reflection line, one-line tease. No long outro.
9. Chapters with curiosity-driven names, not "Part 2".

---

## 10. Data model (initial)

- **Content:** `channel`, `format`, `topic_candidate`, `topic_brief`, `run`, `stage_execution`
- **Research:** `dossier`, `source`, `claim` (maps to `source`)
- **Script and visuals:** `script_version`, `scene`, `shot`, `asset` (provider, prompt, seed, cost, license_record)
- **Audio and video:** `audio_track`, `render`, `qa_report`
- **Publishing:** `package` (titles, thumbnail candidates, description), `publication` (YouTube ids, schedule, veto status), `short_clip`
- **Learning:** `metric_snapshot` (+48h/+7d/+28d), `retention_point`, `scene_attribution`
- **Playbook:** `playbook_rule` (version, rule, evidence, confidence, status), `experiment`
- **Operations:** `cost_entry`, `license_record`, `notification`

---

## 11. Repository structure (proposal)

```
storyforge/
├── orchestrator/          # Spring Boot (JDK 17, Maven)
│   └── src/main/java/.../{run,stage,provider,claude,format,playbook,publish,learn,api,dto}
├── media-svc/             # Python FastAPI: alignment, local TTS, audio QA
├── render-svc/            # Node + Remotion: compositions, components (map, timeline, kinetic text)
├── dashboard/             # React, antd 4, Redux Saga, ApiRoutes.js, react-intl
├── prompts/               # versioned prompt templates per stage
├── schemas/               # JSON Schemas for every Claude output and the EDL
├── library/               # licensed music/SFX + license metadata (gitignored media)
├── config/                # style bibles, formats, playbook seed
├── data/                  # runs, artifacts (gitignored)
├── docs/                  # PROGRESS.md, DECISIONS.md, this spec
└── docker-compose.yml
```

---

## 12. Configuration & profiles

- **`mock`:** every provider is mocked (placeholder images, a sine-wave narration, canned Claude JSON). It runs the full pipeline offline.
- **`local`:** real Claude Code, local TTS/alignment, cheap image provider, no upload.
- **`prod`:** all real providers, YouTube upload enabled.
- Provider choice, models, budgets, schedule, voice id, style bible and format live in `application.yml` plus `config/`.

---

## 13. Cost control

- **Budget cap per video** (default **$60**, configurable). The `CostGuard` estimates the cost before each stage and records actual spend after it.
- **If the projected cost exceeds the cap, degrade gracefully:**
  - fewer motion shots
  - more Remotion graphics
  - cheaper image tier
- **Cost dashboard:** spend per stage, per provider, per video, per month.

---

## 14. Dashboard (supervision)

- **Runs:** a timeline of all runs, with stage status chips.
- **Run detail:** previews of every stage output:
  - topic brief, dossier with sources
  - script with critic scores
  - narration player
  - shot list with thumbnails
  - final video player
  - QA report, packaging candidates
- **Actions:** approve/veto, re-run a stage, swap the thumbnail or title, edit a scene and re-render.
- **Playbook:** the rules with evidence and confidence, the experiments history.
- **Topics:** the backlog and seed list, the scored candidates.
- **Costs,** **Settings,** and a **kill switch** that pauses everything.

---

## 15. Notifications

A Telegram bot or email sends:
- run started, stage failed (with reason)
- **ready for veto** (with a preview link)
- published
- weekly learning report

---

## 16. Testing strategy

- **Unit tests:** the SFM parser, schema validation, scoring, the EDL builder, the cost guard.
- **Provider contract tests:** using recorded responses.
- **Golden test:** render a 20-second sample EDL and compare its duration, frame count and audio levels.
- **End-to-end in the `mock` profile:** `make e2e-mock` produces a full (placeholder) video and a QA report.

---

## 17. Operations

- Docker Compose for all services, with persistent volumes for the DB and `/data`.
- GPU optional (local TTS/alignment are faster with one).
- Nightly backup of the DB and the run manifests.
- Retention policy for heavy intermediate media files.

---

## 18. Known risks & open questions

| Risk | Plan |
|---|---|
| YouTube API projects that aren't verified may have uploads forced to private | Start with private uploads plus manual publish; apply for verification early |
| YouTube Data API quotas | Batch calls, cache results, minimize search calls |
| Claude Code headless authentication in an unattended environment | Validate in M1; document the setup |
| AI visual consistency across a video | Character sheets, fixed seeds, vision QA, fallback shot types |
| Provider price and model changes | Provider interfaces; config-based switching; cost guard |
| YouTube "inauthentic / mass-produced" policies | One deeply researched video per week, a signature voice and style, real analysis; never templated filler |
| Copyright | Guardrails (Section 3) enforced in the Legal QA gate |

---

## 19. Milestones

| # | Milestone | Acceptance criteria |
|---|---|---|
| **M0** | Skeleton + mock end-to-end | Compose up; the `mock` profile produces a placeholder MP4 + QA report from a seed topic with one command; run state machine + dashboard run list |
| **M1** | Brain: Select → Research → Outline → Script → Critique (real Claude Code) | From a seed topic: a dossier with ≥ 8 sources, a script with critic scores ≥ threshold, all outputs schema-valid |
| **M2** | Voice: Narration + Alignment | Narration WAV per scene + word timestamps; mispronunciation re-synthesis works |
| **M3** | Picture: Shot list + images + Remotion assembly | **First real 10+ minute 1080p video file** with matched visuals, camera motion, captions |
| **M4** | Polish: Music/SFX + full QA + Packaging | Mixed audio at −14 LUFS; QA report with all checks; 3 titles + thumbnail + description + chapters |
| **M5** | Autopilot: Scheduler + veto window + YouTube upload (private) + notifications | Weekly run fires by itself; I get a veto notification; the video is uploaded as private with all metadata |
| **M6** | Intelligence: Discover + Learn + Playbook + experiments | Topic discovery from APIs; analytics pulls; per-scene retention attribution; weekly report |
| **M7** | Expansion: Format B (Film Analysis) + Shorts + motion hero shots | A film analysis video passes the Legal QA; 3 Shorts generated per long video |

---

## 20. Definition of done for v1 (M0–M5)

**One command (or the weekly schedule) takes a seed topic and produces:**
- a researched, sourced, critic-approved original script
- a 12–20 minute 1080p video with:
  - a consistent narrator voice
  - visuals matched to every sentence
  - maps and timelines
  - music and SFX
  - captions
- a QA report with no critical issues
- titles, a thumbnail, a description with chapters and sources
- a private YouTube upload awaiting my veto window

…with total cost under budget and **≤ 30 minutes of my time per week.**
