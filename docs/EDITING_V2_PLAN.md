# StoryForge Editing v2: the lean plan (revision 2)

The goal is the biggest jump in quality for the least work, reusing what already exists. Nothing in the app may
*depend* on Gemini: it is an optional booster that the app uses when it is available and skips when it isn't.

**Status: plan only. No code is changed until you say "start".**

---

## 1. What changed in this revision

| Before (revision 1) | Now (revision 2) |
|---|---|
| 7 phases, many new tools | **3 short sprints** + an "optional later" list |
| Gemini as the footage brain | Gemini is **optional**; Claude contact sheets stay the default |
| Face clustering, depth parallax, a new voice model, gl-transitions | **Deferred**: big effort, smaller gain right now |
| New audio libraries (pedalboard, Demucs, Stable Audio) | **Skipped**: licence doubts (GPL, research-only weights, revenue caps). **FFmpeg does EQ, compression, reverb and ducking already**, with no new dependency |
| No channel character | **Your masked BiPath host** appears in short moments (section 3) |

---

## 2. The 3 sprints

### Sprint 1: quick wins (≈ 2–3 days), the biggest visible jump

1. **Finish the footage engine (built, not yet proven).**
   - Restart run 131 (it stopped at narration when the last session closed) and check the result.
   - Fix what the test shows, then commit.
   - Result: up to 70% real film or anime footage, matched to the sentences.
2. **Remotion upgrade (same version, official packages).**
   - `@remotion/effects` gives real light leaks, zoom blur, chromatic aberration, glow, VHS and TV-off effects. They
     replace our home-made versions.
   - `@remotion/transitions` (with overlays) and the faster `@remotion/media` `<Video>`.
   - GPU rendering on your Intel Iris Xe (`gl: "angle"`), plus the official Remotion Claude Code skills for my work.
3. **Real sound design with no new code libraries:**
   - **Sonniss GDC bundle** (free, royalty-free, no credit needed): sorted into whoosh, impact, riser, hit, braam,
     ambience and foley, with an index.
   - Every transition gets **layered** sounds (whoosh + sub hit), every reveal a **riser before it** and an
     **impact on it**, and **every scene an ambience bed** (room, city, rain).
   - **FreePD (CC0) music** as a starter library, sorted by mood.
   - Voice polish with FFmpeg filters: high-pass, presence EQ, gentle compression, de-ess, short room reverb.
4. **Host moments with your character** (section 3): jump-cuts between your 5 still poses, plus the 2 Grok clips,
   all upscaled.

**Sprint 1 test:** the same Fight Club links.
- Expected: real footage most of the time, a pro sound bed, and the host appearing 3–6 times.

### Sprint 2: the Editor (≈ 4–5 days), pro editing decisions

An AI editor (Claude) plans each sentence with **only 7 moves**. Fewer moves done well beats 15 done badly:

| Move | What the viewer sees |
|---|---|
| **Footage** | The film's scene, full screen, with an optional slow-mo or freeze |
| **Headline over footage** | 1–4 bold words popping in sync with the voice (replaces the black kinetic-text cards) |
| **Freeze-frame intro** | Picture freezes, the character is cut out (rembg, Apache-2.0, CPU), a name tag slides in, shutter + bass hit |
| **Film line** | An iconic line (≤ 3 s, max 3 per video) plays from the film with the narration paused and the quote typed on screen |
| **Montage** | 4–8 fast cuts on the music beats (librosa beat grid), used for openers and peaks |
| **Host moment** | Your character "talking" with his hands (section 3), on opinions and direct questions to the viewer |
| **Explainer card** | Map, date, number or quote card, only when it explains better than footage |

**Rules the code enforces:**
- A picture change every 1.5–3.5 s, nothing static for more than 4 s.
- The first 5 s must be the strongest footage plus a headline.
- A pattern interrupt every 25–40 s.
- No move more than 2 times in a row.
- Limits: footage ≤ 70%, AI pictures ≤ 20%, film lines ≤ 3, host time ≤ 10%.

**The writer helps the editor.** The script writes a `@footage:` cue per scene that names the film moment ("Tyler's
soap scene"). Then matching is precise instead of a guess.

### Sprint 3: quality control + reach (≈ 3–4 days)

1. **Automatic editor review:**
   - After rendering, Claude looks at every shot with its sentence and flags off-topic or boring shots.
   - Those shots are replaced automatically.
2. **Chapter-by-chapter rendering:** fixes and your Revise-tab changes re-render one chapter, not the whole video.
   Faster overall.
3. **Shorts:**
   - Vertical crop that follows faces (OpenCV YuNet, MIT, CPU).
   - Big word-by-word captions.
   - The host as the hook.
4. **Thumbnails, 3 per video:**
   - A film close-up with the character cut out + 2–4 big words.
   - Your **masked host** reacting + big words. The mask is a strong brand and works in every niche.
   - A cinematic AI version.

   You put them in YouTube's Test & Compare.

### Optional later (only if the first 3 sprints aren't enough)
- **Gemini catalogue booster.** If a Gemini key or Gemini CLI is available, it watches each footage video (hears
  dialogue, gives timestamps); otherwise Claude does it. Never required.
- Character face clustering.
- 2.5D parallax photos (Depth Anything V2 Small).
- Callouts and split screens.
- gl-transitions (MIT shaders).
- A voice-model test (Chatterbox).

---

## 3. Your masked host (BiPath character)

**What you have:**
- 5 photos in the same studio with different hand poses (`char1`–`char5`) and a face close-up (`charFace`).
- 2 animated Grok clips (6 s, hands explaining, but small at 688×464, and the posters change part-way through the
  second clip).

**Key insight:** the mask has no mouth, so **we never need lip-sync**. Hand gestures, small head moves and cuts are
enough to read as "he's talking", like a masked streamer.

**How the app uses it (Sprint 1, zero cost, works today):**
1. **Jump-cut talking-head.** On a sentence where the narrator talks to the viewer ("Honestly?", "Here's my bold
   claim"), the video cuts between 2–3 of your still poses on the word beats, with tiny punch-ins (zoom 1.0 → 1.08).
   It also adds a breathing scale and flickering LEDs and neon. YouTubers edit real talking heads exactly this way.
2. **Gesture clips.** The Grok clips are upscaled once to 1080p with Real-ESRGAN, which is already installed and runs
   on your Intel GPU. They're trimmed to the parts where the background stays stable and tagged ("explaining",
   "pointing", "leaning in").
3. **Where it appears:**
   - The intro hook (1–2 s).
   - Opinions and questions to the viewer (2–4 s each).
   - Chapter transitions, and the subscribe moment near the end.

   That's 3–6 moments per video, at most 10% of the time. It can also appear in a corner window over footage
   ("commentator mode").
4. **Sound:** a slight "studio mic" EQ on those lines, and a soft chair or cloth foley sound when the host appears.

**Make it better with your Gemini Pro (once, by hand; not an app dependency):**
- Google AI Pro includes **Google Flow (Veo 3.1)**: 1,000 credits/month + 50/day, which is about 50 "Fast" clips or
  100 "Lite" clips a month.
- Use **your photos as the first frame** ("frames to video") so the character and studio stay identical.
- I'll give you **12 ready prompts** for a gesture library, about 8 s each:

  | | |
  |---|---|
  | 1. Explaining with both hands | 7. Thinking, hand on mask chin |
  | 2. Pointing at the camera | 8. Laughing shrug |
  | 3. Leaning in close | 9. Waving hello |
  | 4. Shrugging | 10. Holding up a finger ("one thing…") |
  | 5. Counting on fingers | 11. Arms crossed, nodding |
  | 6. Slow "mind blown" | 12. Pointing to the side (at the footage) |

- Save the results into `assets\character\BiPathVideos`. The app picks them up by itself; if the folder
  is empty, it uses the free still-pose method.
- One generation session gives you a library that lasts for all future videos.
- Check: if a clip has a visible watermark, crop it or regenerate it.

---

## 4. Free tools and sites for cuts, sound and effects (research results)

| Need | Use | Licence / note | Status |
|---|---|---|---|
| Transitions and effects | Remotion `@remotion/transitions` + `@remotion/effects` (~55 effects incl. light leak, zoom blur, chromatic aberration, glitch-like pixel dissolve) | Remotion licence (free for individuals) | **Sprint 1** |
| Extra transition shaders | gl-transitions (~80 GLSL transitions) through Remotion's HTML-in-canvas presentations | MIT (per Remotion docs) | Later |
| Animated arrows, icons, stickers | LottieFiles + `@remotion/lottie` | Lottie Simple Licence (free, commercial OK; check per file) | Sprint 2 |
| Overlays (light leaks, film burn, dust, smoke) | Pixabay videos (we already have the key) | Pixabay licence | Sprint 1 |
| Sound effects library | **Sonniss GDC bundles** | Royalty-free, no credit; not for AI training | **Sprint 1** |
| Specific sound effects | **Freesound** API, CC0 filter | CC0 only | Sprint 1 (needs a free key) |
| Game-style UI sounds | Kenney audio packs | CC0 | Sprint 1 |
| Music | **FreePD** | CC0 | **Sprint 1** |
| More music | Pixabay Music, YouTube Audio Library | Free, downloaded by hand | Your choice |
| Mixing (EQ, compression, reverb, ducking, loudness) | **FFmpeg** filters (`equalizer`, `acompressor`, `afir`, `sidechaincompress`, `loudnorm`) | Already installed | **Sprint 1** |
| Reverb rooms | OpenAIR impulse responses | Mostly CC BY (credit in description) | Optional |
| Beat timing for montages | librosa | ISC (permissive) | Sprint 2 |
| Cut-outs (freeze-frame intros, thumbnails) | rembg with the u2net model | MIT code, Apache-2.0 weights; avoid RMBG-2.0 (non-commercial) | Sprint 2 |
| Face positions (Shorts crop, callouts) | OpenCV YuNet | MIT | Sprint 3 |
| Scene cuts | FFmpeg scene detection (have), PySceneDetect | BSD | Have |
| Upscaling host clips | Real-ESRGAN (already installed) | BSD | Sprint 1 |
| Claude Code skills | **Remotion official skills/plugin** (adopt); kajisho5/ffmpeg-skill (MIT, 42 FFmpeg tools) and Claude-Code-Video-Toolkit (reference) | — | Sprint 1 |
| Video brain (optional) | Gemini API free tier (YouTube links, ~8 h/day) or Gemini CLI with your Pro login | Optional; the app works without it | Later |
| Host clips | Google Flow / Veo 3.1 with your AI Pro, made by hand once | Credits included in your plan | Your choice |

**Rejected:**
- BBC Sound Effects (non-commercial licence).
- Stable Audio Open (revenue-capped licence).
- Demucs weights (research-only).
- Spotify pedalboard (GPL; FFmpeg does the same job).
- Kling free tier (no commercial use on the free plan).
- AI video generation inside the app (needs a big GPU or money).
- DaVinci Resolve MCP (needs the paid Studio version).
- Any trick to fool Content ID.

---

## 5. How we'll know it worked (checked on every video)

| Metric | Today | Target |
|---|---|---|
| Real footage (film/anime topics) | 4.5% | 60–70% |
| Time between picture changes | 4.4 s | 1.5–3 s |
| First 15 s | empty backgrounds | ≥ 4 footage cuts + a headline + layered sound |
| Sound | synthesized | library sounds, layered, with ambience under every scene |
| Host moments | none | 3–6 per video, ≤ 10% of the time |
| Off-topic shots after review | not checked | 0 |
| Render time, 10-minute video | ~90 min | ≤ 60 min |

---

## 6. What I need from you

**Sprint 1:**
- A "start".
- ~10 GB free disk space (the Sonniss bundle is ~7.5 GB).
- Optionally, a free Freesound key.

**Whenever you like:**
- Generate the 12 host clips in Google Flow with my prompts.

**Optional later:**
- A Gemini API key, or keep Gemini CLI logged in on this PC.

## 7. Sources

- **Remotion:**
  - [effects list](https://github.com/remotion-dev/skills/blob/main/skills/remotion-best-practices/remotion-markup/effects.md)
  - [transitions](https://www.remotion.dev/docs/transitions/transitionseries)
  - [gl-transitions in Remotion](https://www.remotion.dev/docs/transitions/presentations/custom-html-in-canvas)
  - [Claude Code plugin](https://remotion.dev/docs/ai/claude-code-plugin)
  - [GPU (angle)](https://www.remotion.dev/docs/three)
- **Google AI Pro:**
  - [1,000 Flow credits](https://whizi.io/resources/google-ai-pro-limits/)
  - [Veo pricing and credits](https://diyai.io/ai-tools/video-generation/google-veo-pricing/)
  - [Gemini CLI headless](https://geminicli.com/docs/cli/headless.md)
  - [Gemini video understanding](https://ai.google.dev/gemini-api/docs/video-understanding)
- **Sound:**
  - [Sonniss GDC 2026](https://rekkerd.org/sonniss-releases-gdc-2026-game-audio-bundle/)
  - [Freesound CC0 API](https://opensource.creativecommons.org/blog/entries/freesound-intro/)
  - [FreePD CC0](https://a.ocv.me/pub/stuff/music/freepd/README.md)
  - [Stable Audio Open licence](https://www.deeplearning.ai/the-batch/stability-ai-releases-enhanced-text-to-audio-generator-stable-audio-open)
  - [Demucs weights question](https://huggingface.co/adefossez/HTDemucs/discussions/1)
  - [DeepFilterNet](https://github.com/lucribas/DeepFilterNet)
- **Tools:**
  - [rembg licences and speed](https://railway.com/deploy/background-removal-api-or-just-updated-r)
  - [OpenCV YuNet](https://huggingface.co/opencv/face_detection_yunet/blob/main/README.md)
  - [ffmpeg-skill](https://github.com/kajisho5/ffmpeg-skill)
  - [Claude Code Video Toolkit](https://github.com/wilwaldon/Claude-Code-Video-Toolkit)
  - [Kling free-plan terms](https://marcandrews.com/?p=6319)
  - [DaVinci MCP needs Studio](https://github.com/samuelgursky/davinci-resolve-mcp)
