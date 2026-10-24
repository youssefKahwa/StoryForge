# StoryForge: the complete guide

StoryForge turns 1–3 YouTube links (Arabic, Darija, French, anything) or just a topic into a finished English
long-form YouTube video, plus Shorts, thumbnails and everything you need to publish. It runs on your own PC, only
when you start it, and uses only free services.

This guide explains everything the app does, step by step, and everything it can use.

---

## 1. Status of the work (read this first)

| Part | State |
|---|---|
| Pipeline, dashboard, live progress, Quick start, revisions, fair-use trailer clips, Pixabay/NASA/Commons media, globe maps, talking logo, BiPath style, transitions with sound | Done, tested, committed |
| Smart fallback when no AI picture can be made (stock photo, then reuse) | Done, tested, committed (`b8604c4`) |
| **v3** (your last request): human script that follows your links, 8+ minutes, wait for the picture quota instead of empty screens, full-screen pro edit, punch-ins, glitch, film burn, impact, dip to black, new sounds | **Built and unit-tested, NOT yet tested on a real video, NOT committed yet** |

The full v3 test with your 3 Fight Club links was started and then stopped when you asked. It will continue when you
say so.

---

## 2. How to use it (every week)

1. Plug in the charger, then run `.\scripts\start.ps1` in PowerShell from the StoryForge folder.
   It builds what is needed, starts everything and opens **http://localhost:8080**.
2. **New video → Quick start**: paste 1–3 YouTube links and/or a topic (for example "Movie Fight Club") and click
   **Make my video**. You can also give nothing and the app picks a topic.
3. Watch the progress page. It shows the percentage, the current step, what's left and the expected finish time.
4. When it's done, open the run:
   - **Revise** tab: point at anything you want redone (a scene, a picture, the narration, the music, the title).
   - **Publish kit** tab: video, Shorts, thumbnails, title, description, tags.
   - The same files are in `C:\Users\<you>\storyforge-data\exports\<date>-<title>\`.
5. Close the PowerShell window when you're done. An unfinished video continues where it stopped the next time you
   start the app.

A 10-minute video takes about 2 to 2.5 hours on this PC. Most of that time is spent rendering.

---

## 3. What the app is made of

| Part | What it does | Tech |
|---|---|---|
| **Orchestrator** (`orchestrator/`) | The brain: runs the 17 steps, stores everything, serves the dashboard and the API | Java 17, Spring Boot 4, H2 database |
| **Dashboard** (`dashboard/`) | The website at localhost:8080: new video, progress, revise, settings | Gatsby / React |
| **media-svc** (`media-svc/`) | Voice (text to speech), word timing, YouTube transcripts, trailer download, sound effects | Python, FastAPI, Kokoro, Whisper, yt-dlp |
| **render-svc** (`render-svc/`) | Draws and renders the video, the Shorts and the thumbnails frame by frame | Remotion (React video), Chrome |
| **Prompts** (`prompts/`) | The instructions given to the AI writer at each step | Markdown files |
| **Formats** (`config/formats/`) | The two video styles: Great Stories and Film Analysis | JSON |

Your data lives in `C:\Users\<you>\storyforge-data\` (runs, exports, caches, music, sound effects, trailers,
database).

---

## 4. The 17 steps of a video

Each step saves its files in `storyforge-data\runs\<number>\<step>\`. If something fails, the app restarts from that
step, not from zero. Answers from the AI are cached, so a rerun does not spend your Claude quota again.

| # | Step (as shown on the progress page) | What happens |
|---|---|---|
| 0 | *(before the run)* Reading your links | Downloads the transcripts of your 1–3 videos (or transcribes them with Whisper). The AI reads them all and writes the **blueprint** (their running order, every point, opinion, joke and question) and their **voice** (how they talk). |
| 1 | Getting the topic ready | Takes your topic, your links' ideas or a topic idea. |
| 2 | Choosing the angle | Picks the best angle and title promise for our version. |
| 3 | Researching sources | Searches the web (Claude WebSearch/WebFetch) and builds a fact file: claims with sources, dates, numbers, quotes, "wow facts". At least 8 sources. |
| 4 | Planning the chapters | Builds the outline. With links, it follows **your videos' order and points**. Without links, it uses the format's beats (hook, context, recap, analysis…). |
| 5 | Writing the script | Writes the full narration in **StoryForge Markup** (scenes, tone, pauses, picture ideas, sound cues, music mood). |
| 6 | Reviewing and polishing | A separate AI critic scores the script (hook, clarity, pacing, facts, **human voice**…). If too low, it is rewritten (up to the limit), then trimmed to the exact length. |
| 7 | Recording the narration | Kokoro voice reads each scene (free, runs on your PC). |
| 8 | Syncing words to the voice | Finds the exact time of every word (for cuts, captions, punch-ins and the talking logo). |
| 9 | Planning every shot | The "director": cuts the narration into shots of 3–6 s, writes a picture prompt per shot, decides which shots get a real photo, real footage, a trailer clip or a map, and writes the on-screen texts. |
| 10 | Finding photos, footage and pictures | Gets every visual: real media, trailer clips, AI pictures (see section 6). |
| 11 | Mixing music and sound | Music per scene (ducked under the voice), sound cues, transition sounds, loudness to YouTube's -14 LUFS. |
| 12 | Rendering the video | Remotion draws every frame (1920×1080, 30 fps) with all the edit effects (see section 7). |
| 13 | Quality checks | Automatic checks: resolution, sound level, sync, black frames, length, every shot has a picture, sources, licenses, originality (no copied sentences), fair-use clip share. |
| 14 | Packaging | Title, description with chapters and sources, tags, 3 thumbnails, 3 Shorts, export folder. |
| 15–17 | Schedule, publish, learn | Prepares the upload (private first, you can veto). Uploading needs your YouTube OAuth set up (not done yet). |

---

## 5. How the script is written (v3)

What you asked for: "read carefully what my links say and do something like that, not robotic".

- **Reading your links**: the AI reads every transcript fully and extracts:
  - **Blueprint**: 5–9 parts in their exact order, with every point they make. For Fight Club this included the
    furniture vs yearly phone upgrade comparison, "people there truly listen", "don't copy Tyler", the empty-gun
    store scene, and the flop and then the DVD success.
  - **Voice**: for example "a film fanatic who has watched it several times, fast and excited, tells the viewer to
    focus, jokes about daily life, invites disagreement".
- **Outline**: follows the blueprint order and covers all its points. Plot recap stays under 40%; the rest is takes
  and analysis.
- **Script rules**:
  - It is written as a person talking to one viewer: "you", "I", contractions, opinions said as opinions, reactions,
    questions, humor, and the plot told like a story in the present tense.
  - Banned: essay words ("furthermore", "profound", "delve"), "according to scholars", "sources differ",
    "in this video", "let's dive in".
  - At most one dramatic pause per scene, placed before a reveal. The edit uses these pauses (see 7.3).
- **Facts**: real-world facts (dates, money, names) must come from the researched sources. Plot events must match the
  story. Opinions are free.
- **Close adaptation, not translation**: same story, same order, same points, same energy, but our own English
  sentences. Their sentences are never translated line by line. Why:
  1. A translated script is still the other creator's work.
  2. YouTube's monetization review rejects "reused" or "inauthentic" content, and that review is exactly what you
     need to pass to earn money.

  The originality check in QA verifies this.
- **Channel brief** (Settings): "a passionate, funny, opinionated storyteller who talks to the viewer like a friend".
  You can edit it.
- **Voice**: Kokoro, free and local. Current voice: `af_heart`, a female US voice and Kokoro's best-rated one. There
  are samples to compare in `storyforge-data\samples\voices-v3\`: `am_michael.wav`, `am_fenrir.wav`,
  `bm_george.wav`, `af_heart.wav`. Choose one in **Settings → Default voice**.

---

## 6. Where the pictures and videos come from

The goal is to never show an empty screen, and to use as much real material as possible.

**Order per shot:**

1. **Map** (for at most 2 shots per video): an animated 3D globe that turns and zooms to the place being talked
   about. It uses Wikidata coordinates and Natural Earth maps (both free and public domain).
2. **Fair-use trailer clips** (only when the video is about a film, series, anime or game):
   - The app finds the official trailers itself (up to 4: official trailer, teaser, trailer 2, international), using
     yt-dlp, picture only, no sound.
   - It detects every cut and shows Claude numbered contact sheets. Claude matches trailer moments to what the
     narrator says.
   - Limits enforced in code:
     - A share of the video set in Settings (default **20%**, maximum 25%).
     - Each clip uses at most 4.5 s of the trailer, slowed down (at least 0.5×) with a short freeze.
     - At least 8 s between clips.
     - No title or logo cards, muted, with a credit in the corner.
   - QA lists every clip, and the description credits them.
   - A Content ID claim is still possible; watch the clips before publishing.
3. **Real photos**: Wikimedia Commons (public domain or CC licenses, with attribution), then NASA (public domain),
   then Pixabay photos.
4. **Real footage**: Pixabay videos (landscape only, cleaned), Wikimedia Commons video, and Pexels if you ever get a
   key.
5. **AI pictures**:
   - Cloudflare Workers AI, model FLUX.1-schnell, free tier of about 165 pictures per day, reset at 01:00 your time
     (00:00 UTC).
   - Film Analysis style (v3): cinematic film stills, moody 35mm, dramatic light, anonymous people (faces in shadow
     or turned away, never a real actor's face). Great Stories style: painterly cinematic illustration.
   - A "visual bible" keeps the same characters and places looking the same in every picture.
6. **If an AI picture cannot be made** (quota used up):
   - **v3**: if the video needs more new pictures than today's quota has left, the app **waits for the reset at
     01:02 your time** and then continues. The progress page shows "Waiting for the free picture quota…". Keep the
     app open, or start it again later and it continues.
   - Leftover gaps: the AI suggests a stock photo search (checked against the narration, off-topic photos are
     rejected), else the nearest picture is reused with a different framing.
   - Empty backgrounds can no longer happen: QA fails the video if one appears.

A 10-minute video needs about 80–100 AI pictures, so 1 video per day fits the free quota easily. Thumbnails keep 3
pictures in reserve.

---

## 7. The montage (editing)

This is what makes it look like a YouTube channel and not a slideshow.

### 7.1 How pictures are shown (v3)
- **Everything full screen**:
  - AI pictures: a camera move (push in, pull out, pan, with a slight rotation), eased like a gimbal.
  - Real photos: full screen over a blurred copy of themselves (no white "print" frame), with a slow drift.
  - Footage and trailer clips: full screen, slow push-in, light color grade, small credit bottom-left. The old
    "FROM THE TRAILER" box is gone.
- **The picture changes every 2–3 seconds**: shots are 3–6 s long, and every shot longer than 3.2 s gets a
  **punch-in** halfway through (same picture, tighter crop on another area, a tiny camera flash and a quiet shutter
  click). This is the "frame switch" of current edits, and it costs no extra AI pictures.
- **Film look** on top of everything: soft vignette and light film grain.

### 7.2 Transitions with sound (v3)
The default is a **hard cut** (pro editors cut, they don't fade). On the cuts that matter, the app picks a transition
and the matching sound, timed so the sound peaks exactly on the cut:

| When | Picture effect | Sound |
|---|---|---|
| New chapter | **Film burn / light leak** or **zoom-through** (alternating) | film burn whoosh + hit / zoom whoosh |
| A reveal (the narrator pauses inside a scene) | **Impact**: camera shake + small flash | deep cinematic **boom** |
| A big reveal (long pause, max 2 per video) | **Dip to black**, then fade in | **sub drop** (bass drop) |
| Tense scene change | **Glitch** (RGB bars, jitter) or **white flash** (alternating) | digital glitch / flash hit |
| On-screen key phrase | soft zoom | soft swoosh |
| Every ~14 s without a transition | **whip pan** or zoom (alternating) | whip / whoosh |
| Calm or sad scene change | slow crossfade | none |
| Punch-in inside a shot | tighter crop + tiny flash | quiet camera shutter click |

Energetic transitions are at least 6 s apart so it never becomes noisy. All sounds are generated by code (noise and
tones), so there are no copyright problems.

### 7.3 Text and graphics (BiPath brand style)
- Fonts and colors match your BiPath logo: white and sky blue, bold extruded titles, snow particles.
- **Chapter cards** at each new chapter. **Kinetic text** (a key phrase over a blurred picture) about once a minute.
- **Overlays** checked against the narration: dates, places ("VENICE, ITALY / SEPTEMBER 10, 1999"), numbers, quotes.
- Captions can be burned in (Settings). A captions file (`captions.srt`) is always exported.

### 7.4 The talking logo (host)
Your `ChannelAssets\DownRightTalkingMouth.mp4` logo appears in a circle at the bottom right at chosen moments
(questions, key lines). Its mouth moves with the loudness of the narration (lip-sync). It can be switched off in
Settings.

### 7.5 Sound
- **Narration**: Kokoro voice, with pauses from the script.
- **Music**:
  - Comes from your library `storyforge-data\library\music\<mood>\` (royalty-free tracks).
  - A mood is chosen per scene and ducked under the voice.
  - Volume is set in Settings (off, low, normal, high).
- **Sound cues** from the script (for example a clock tick on "insomniac"), the transition sounds above, and the
  punch-in clicks.
- **Final loudness**: -14 LUFS with true peak under -1 dB (YouTube standard).

### 7.6 Rendering
- Remotion renders in Chrome with several tabs. The number of tabs follows your free RAM, and on battery it uses 2
  tabs.
- If Chrome runs out of memory, the render retries automatically with fewer tabs.
- A 6-minute video took about 55 minutes on this PC, so expect about 90 minutes for 10 minutes.

---

## 8. What you get at the end (Publish kit)

- `video.mp4`: the long video, 1080p.
- `short_1/2/3.mp4`: vertical 1080×1920 Shorts.
  - Each is cut from the strongest moments, with a big title, word-by-word captions and a "Full story on the
    channel" end card.
  - They keep the same cuts and transition sounds as the long video.
- `thumbnail_1/2/3.png`: 3 thumbnail options (AI background or a real photo, plus big bold text).
- `description.txt`: hook, **chapters with timestamps**, sources, credits for photos, footage and clips.
- `tags.txt`, `captions.srt`, `publish_kit.md` (title options and the checklist).
- QA report: `runs\<n>\qa\qa_report.html`.

---

## 9. Changing things after the video is made (Revise)

On the run page, **Revise** tab, you point at what to redo:

| You point at | The app redoes |
|---|---|
| A scene's text ("make this funnier", "wrong fact") | Rewrites that scene, re-records, re-renders |
| The narration | Records again |
| A picture or shot | New picture(s) for those shots only |
| The music | New music choice or volume |
| The package (title, thumbnail, description) | Packaging only |

Then **Apply**: only the needed steps run again. You can also **Re-run from a step** (for example "assets" after the
picture quota resets).

---

## 10. Dashboard pages

- **Home**: all runs with their status and a live progress column.
- **New video**: Quick start (links, topic or nothing), or the step-by-step wizard (choose the angle and length
  yourself).
- **Run page**: live progress (steps, %, time left, finish time), video player, Revise, Publish kit, QA report,
  costs (always $0).
- **Topics**: topic ideas generated for your channel, and seed topics.
- **Settings**:
  - Default voice, default length (minimum **8 min**, default **10**), format.
  - Shorts on/off and how many.
  - Burned captions, image upscaling, music volume.
  - Publish day and time.
  - Channel brief.
  - Talking logo on/off.
  - Fair-use trailer clips on/off and **max share %**.
  - **System check**: is everything installed and are the keys working, how many free pictures are left today.
  - **Storage cleanup**: delete old runs and caches.

---

## 11. Free services and keys

| Service | Used for | Key | Limit |
|---|---|---|---|
| Claude Code (your Pro subscription) | Thinking and writing: analysis, research, script, critic, director, clip matching | your login | Pro quota (used sparingly, answers cached) |
| NVIDIA free API | Backup text model | `.env` | free tier |
| Cloudflare Workers AI | AI pictures (FLUX.1-schnell) | `.env` (account ID + token) | ~165 pictures/day, reset 00:00 UTC |
| Pixabay | Photos and footage | `PIXABAY_API_KEY` in `.env` | free, rate-limited (the app throttles) |
| Wikimedia Commons, NASA, Wikidata | Real photos, video, map coordinates | none | free |
| Kokoro (local) | Narration voice | none | runs on your PC |
| Whisper (local) | Transcripts and word timing | none | runs on your PC |
| yt-dlp | Reading your links, trailers | none | — |
| Pexels | Footage | optional (new keys paused) | — |
| Telegram | "Your video is ready" message | optional | — |
| YouTube Data API (OAuth) | Upload as private | optional, not set up yet | — |

Secrets live only in `.env`, which is never committed to git. The Cloudflare token should still be rolled with
`.\scripts\set-cloudflare-token.ps1`, because it was pasted in a chat earlier.

---

## 12. Copyright and YouTube rules the app follows

- The script is in our own words. Originality is checked against the sources and transcripts (no long shared phrases).
- AI pictures never show a real person's identifiable face or a celebrity likeness. Realistic AI scenes switch on
  YouTube's "altered or synthetic content" label (this does not affect monetization).
- Real photos and footage are only used with an allowed license (public domain, CC0, CC BY/BY-SA, Pixabay license),
  with attribution in the description.
- Trailer clips use fair-use limits: short, muted, under commentary, credited, with a share set by you. A Content ID
  claim is still possible, and QA warns you.
- No tricks to dodge Content ID (no mirroring or pitch shifting): that is against YouTube policy.
- Music and sound effects: only licensed tracks from your library, and sound effects generated by code.
- Long-form videos are at least 8 minutes, the minimum for mid-roll ads.

---

## 13. Useful folders

| Folder | What's inside |
|---|---|
| `storyforge-data\runs\<n>\` | Everything for one video, step by step (script, shots, pictures, audio, edl.json, render log, QA) |
| `storyforge-data\exports\` | Finished publish kits |
| `storyforge-data\inspirations\<n>\` | Transcripts of your links + the blueprint (`ideas.json`) |
| `storyforge-data\library\music\<mood>\` | Your music |
| `storyforge-data\library\sfx\` | Generated sound effects |
| `storyforge-data\library\trailers\` | Downloaded trailers (picture only) |
| `storyforge-data\cache\` | AI answers, pictures, voice (so reruns are free and fast) |
| `docs\` | SPEC, PROGRESS, DECISIONS (every design choice, ADR-001 to ADR-027), OWNER_SETUP |

---

## 14. If something goes wrong

- **Progress page stuck on "Waiting for the free picture quota"**: normal. It continues at 01:02 your time if the app
  is open.
- **A run failed**: open it, read the reason, then click **Resume** or **Re-run from** the step.
- **One link could not be read**: the video continues with the other links. Transcripts are now reused, so a link
  already read is never transcribed again (v3).
- **Render is slow**: plug in the charger and close Chrome tabs. On battery it is about 3× slower.
- **System check is red**: follow the hint shown next to the item.

---

## 15. What's next

1. Finish the v3 test with your 3 Fight Club links (10 minutes), check the result, then commit v3.
2. Choose a narrator voice from the 4 samples.
3. Optional: set up YouTube upload (OAuth) and Telegram notifications, and roll the Cloudflare token.
