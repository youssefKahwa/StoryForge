# Why our videos look weak, and how to make them pro

Diagnostic of the current output, plus research into the internet, the Claude community and free open-source
projects. Written on 2026-10-24.

---

## 1. The short answer

The main problem is not the transitions or the effects. **The pictures are not the story.**

- In a film video, the viewer wants to *see the film*: the scene the narrator is talking about, right when they talk
  about it.
- In a true story, the viewer wants the real place, the real people and real archive footage.

Today StoryForge mostly shows generic stock photos, symbolic AI images and empty backgrounds. Even with perfect
editing, that looks like a slideshow, because it *is* one.

What pro channels do, and what we can copy for free:
1. **Real footage of the story as the base layer**: film scenes for film videos, archive footage for true stories.
   These should be 50–80% of the screen time, not 5%.
2. **Every shot chosen for the exact sentence being said.** Open-source tools now do this automatically:
   - NarratoAI finds the moments in a film with a vision model.
   - CLIP or SigLIP match narration text to video frames.
   - FunClip cuts a film by searching its subtitles.
3. **Motion on everything**: 2.5D parallax on photos (open-source depth AI), a fast cut rhythm, and a real sound
   design library instead of synthesized beeps.
4. **A visual quality loop**: Claude watches the finished video's frames and redoes the weak shots before you see it.

---

## 2. Diagnostic: what is wrong today (evidence from run 98, Fight Club)

| # | Problem | Evidence | Impact on views |
|---|---|---|---|
| 1 | **Visuals are not related to the story** | Only 3 film clips (16 s, 4.5% of the video). The rest: 36 empty backgrounds, 26 stock photos (a DVD case, a spinning top, holding hands…), 5 generic Pixabay videos | Very high. Viewers leave in the first 30 s when the picture doesn't match the words |
| 2 | **The hook is empty** | The first 49 s are almost only empty starry backgrounds | Very high. The first 30 s decide whether YouTube pushes the video |
| 3 | **Slideshow rhythm** | 81 shots in 6 min = one change every 4.4 s, slow Ken Burns zooms, crossfades | High |
| 4 | **"Presentation" framing** | Photos as tilted white prints, clips in a framed box labelled "FROM THE TRAILER" | High (fixed in v3, not yet tested) |
| 5 | **Robotic script** | Essay tone, "according to…", "sources differ" | High (fixed in v3, not yet tested) |
| 6 | **Thin sound design** | Every sound effect is synthesized from noise and sine waves | Medium. Pro edits use recorded whooshes, impacts, risers and textures |
| 7 | **Text graphics look cheap** | Kinetic text over a blurred image about once a minute | Medium |
| 8 | **AI images look "AI"** | Flat symbolic style for film analysis | Medium (v3 switches to cinematic stills, but they are still not the real film) |
| 9 | **TTS voice** | Kokoro is good but flat for a passionate film-review style | Medium. Choose from the samples, and the v3 script helps |

v3 (built, not yet tested) fixes 4 and 5 and part of 2, 3 and 8. **It does not fix #1**, which is the biggest one.

---

## 3. What top channels do (film analysis, recap, documentary)

- **Film channels** (essay and recap): the film itself fills most of the screen. Every sentence has the exact matching
  shot, often several cuts per sentence. Film channels also add commentary, analysis and an opinion. That is also the
  strongest fair-use position, because "a plain plot summary is the weakest position to be in"
  ([usethirdchair](https://usethirdchair.com/blog/youtube-fair-use-what-actually-counts-in),
  [vidiq](https://vidiq.com/es/blog/post/can-you-monetize-anime-clips-recaps-gameplay-youtube/)).
- **Documentary channels**: real archive footage, real photos with 2.5D parallax, animated maps, and footage that is
  never static. Pro editors are asked for "tight pacing, zero dead air, momentum from the first 8 seconds", "subtle
  motion (pan / zoom / parallax)", and "layered SFX and leveled VO"
  ([djinni job brief](https://djinni.co/jobs/838331-faceless-youtube-video-editor-long-form-illus/),
  [fiverr](https://fiverr.com/bilawal_visuals/edit-high-retention-faceless-youtube-documentaries)).
- **Pattern interrupts**: quick cuts, sound effects, zoom-ins and pace changes
  ([pippit/capcut](https://pippit.capcut.com/resource/top-tips-for-building-audience-retention-on-faceless-youtube-channels)).
- **The parallax "photo comes alive" look** is a classic of documentary editing, made famous by *The Kid Stays in the
  Picture* ([waxy.org](https://waxy.org/category/posts/page/6)).

---

## 4. Research: free and open-source things we can use

### 4.1 Projects that already solve "the right clip for the right sentence"

| Project | What it does | Useful for us | License / needs |
|---|---|---|---|
| **NarratoAI** ([GitHub](https://github.com/linyqh/NarratoAI)) | AI **film commentary**: you upload the video, it extracts frames, a vision model understands the scenes, an LLM writes the commentary, then it cuts the matching clips, dubs and subtitles | The closest project to what you want. We copy its idea: index the film's scenes with a vision model, then pick scenes per narration line | MIT, no GPU needed (4 cores, 8 GB) |
| **FunClip** (Alibaba, [GitHub](https://github.com/modelscope/FunClip)) | Transcribes a video, then an LLM picks the moments, and it cuts by subtitle timestamps | **Dialogue moments**: when the narrator says "Tyler says the things you own end up owning you", search the film's subtitles and cut exactly that line | MIT, local |
| **CLIP / OpenCLIP / SigLIP** semantic video search ([Vultr guide](https://docs.vultr.com/semantic-video-frame-search-using-openai-clip-and-vector-database), [semantic-eye](https://github.com/GughanS/semantic-eye), [VCED](https://www.sourcepulse.org/projects/1861602)) | Scene detection, then an embedding per frame, then search by text ("a man burning his hand", "buildings collapsing at night") | Ranks every film scene, archive clip and stock clip against each narration sentence. Fast on CPU with small models | Open models |
| **PySceneDetect** | Finds every cut in a video | Splits a film or trailer into scenes (we already do a basic version with FFmpeg) | BSD |
| **MoneyPrinterTurbo / ShortGPT** | Script → Pexels/Pixabay keyword footage → video | **Not better than ours**: keyword stock footage is exactly the "generic" problem. Not recommended | MIT |

### 4.2 Claude community tools

| Tool | What it gives us |
|---|---|
| **Remotion's official Claude Code plugin and skills** ([remotion.dev](https://remotion.dev/docs/ai/claude-code-plugin), [tella guide](https://www.tella.com/blog/how-to-use-remotion-agent-skills-with-claude-code)): `/remotion-best-practices`, `/remotion-captions`, `/remotion-maps`… | Remotion's own maintained rules. We use Remotion already, so installing it makes my work on the editing engine better and safer |
| **mcp-video** (Apache-2.0, [listing](https://mcpservers.org/hi/servers/kyanitelabs/mcp-video)): FFmpeg editing, effects, subtitles, media analysis for AI agents | Lets Claude inspect and analyse media directly. Optional |
| **Claude's own vision** (we already use it for trailer contact sheets) | Extend it to (a) describe every film scene once, and (b) **review the finished video** frame by frame and send weak shots back for a redo |

### 4.3 Where real, story-related footage can come from

**For film, series and anime videos:**

| Source | How much material | Risk | Notes |
|---|---|---|---|
| **A. The film file itself** (you provide a copy you own, plus its subtitles) | Everything: every scene, matched to every sentence | **Highest Content ID risk**: claims can take the ad money for that video. Fair use is strongest with real commentary and short clips | This is what recap and film-essay channels do. Content ID "can claim videos that may still be fair use" ([usethirdchair](https://usethirdchair.com/blog/youtube-fair-use-what-actually-counts-in)) |
| **B. Official promo videos on YouTube**: trailers, teasers, TV spots, featurettes, official "clips" uploaded by studios | 5–20 min of real scenes per film | Medium. Same as today's trailers | Big improvement over 1 trailer, and no file needed from you |
| **C. Film stills** (TMDB backdrops) | 10–50 stills per film | Unclear terms: TMDB's commercial use may need an agreement ([TMDB forum](https://www.themoviedb.org/talk/530525ae9251413488038dc4), [api.market](https://api.market/blog/sleeyax/entertainment/best-movie-apis)) | Not recommended without checking their terms |

**For true stories, history and mysteries:**

| Source | Notes |
|---|---|
| **Internet Archive** (Prelinger and other public-domain films) | About 60–65% of Prelinger is public domain and usable for any purpose, with a suggested credit ([Wikipedia](https://en.wikipedia.org/wiki/Prelinger_Archives), [archive.org blog](https://blog.archive.org/tag/stock-footage/)). There is a free search API and the `internetarchive` Python tool ([PyPI](https://pypi.org/project/internetarchive/0.8.5/)). Check the license per item |
| Wikimedia Commons, NASA, Pixabay | Already in the app. Rank results with CLIP instead of tags so off-topic results disappear |
| Coverr, Mixkit, Videvo | Good free libraries, but no API and mixed licenses ([colorlib](https://colorlib.com/wp/best-free-stock-video-sites/)). Not automatable |

### 4.4 Motion for still pictures (no GPU on this PC)

| Option | Verdict |
|---|---|
| AI image-to-video (Wan 2.2, LTX) | Needs 16–24 GB of GPU memory ([spheron](https://www.spheron.network/blog/image-to-video-gpu-cloud-ltx-wan-hunyuan/)). Hosted versions cost money (about $0.05–0.07 per second). Free Hugging Face Spaces exist, but with small, unreliable quotas. **Not usable for free at our volume** |
| **2.5D parallax with Depth Anything V2 Small** | Depth map on CPU (about 1–2 s per picture), then the picture is split into 2–3 depth layers that move at different speeds in Remotion. That gives the "photo comes alive" documentary look on *every* still. DepthFlow does it on the GPU ([HF](https://www.huggingface.co/spaces/BrokenSource/DepthFlow/blob/main/README.md)); we would do the CPU-friendly layered version. **Recommended** |

### 4.5 Sound design

| Source | Verdict |
|---|---|
| **Sonniss GDC Game Audio Bundles**: 7.47 GB in 2026 alone, plus a free archive covering 2015–2024. Royalty-free, no attribution, unlimited projects ([rekkerd](https://rekkerd.org/sonniss-releases-gdc-2026-game-audio-bundle/), [zeli](https://zeli.app/en/story/49262841)) | **Recommended.** Real cinematic whooshes, impacts, risers and textures. One-time manual download by you. (Its license bans AI training, not use in videos) |
| **Freesound API** with a CC0 filter (free key) ([Creative Commons](https://opensource.creativecommons.org/blog/entries/freesound-intro/)) | Good for specific sounds ("clock ticking", "rain on window") searched automatically from the script's sound cues |
| Our synthesized sounds | Keep only as a fallback |

---

## 5. Recommended plan

### Phase 1: Real footage first (the biggest win)
1. **Film mode with a file (option A)**: in Quick start you can drop the film file. The app then:
   - Cuts the film into scenes and transcribes its dialogue, or uses the subtitle file.
   - Has Claude describe each scene once from contact sheets (cached), and adds CLIP embeddings.
   - For every narration sentence, picks the best scene: quoted dialogue is found by subtitle search, visual moments
     by description and embedding.
   - Uses 1.5–4 s clips, full screen, under the commentary, never the film's own audio.
   - Limits film footage to a share you choose (for example 50–70%), so the rest is our own material: maps, graphics,
     cinematic AI images.
2. **No file (option B)**: collect *all* official promo videos (trailers, teasers, TV spots, featurettes, official
   clips) instead of one trailer, and match them the same way.
3. **True stories**: Internet Archive public-domain footage and Commons, ranked by CLIP against each sentence.
4. AI images only fill gaps, never more than about 30%.

### Phase 2: Edit like a pro
1. **Cut rhythm**: a visual change every 1.5–3 s, mostly real footage. Punch-ins and the v3 transitions stay.
2. **2.5D parallax** on every photo and AI still (Depth Anything V2 Small, CPU).
3. **Real sound design**: Sonniss and Freesound sounds replace the synthesized ones. Every cut, reveal and chapter
   gets layered sounds plus room tone.
4. **Better text**: fewer cards; bold animated keywords synced to the voice; lower-thirds for names, places and
   dates.
5. Install **Remotion's official Claude Code skills** so the editing engine follows Remotion's best practices.

### Phase 3: Automatic visual review
After the render, Claude reviews a contact sheet of every shot with the sentence being said. It answers: is this
picture about this sentence, and is it interesting? Weak shots are replaced automatically before the video is
marked ready.

### Phase 4: Voice and script
v3 human script (already built), a chosen narrator voice, and a hook rule: the first 30 s must show the strongest
real footage.

---

## 6. Decisions only you can make

1. **Film file mode (option A)**: do you accept the higher Content ID risk? A claim can take the ad revenue of one
   video; it does not usually hurt the channel. If not, we use option B only (official promo videos).
2. **Maximum film footage share**: for example 50%, 70% or 80%.
3. **Sonniss**: can you download the free GDC bundle once (several GB)? I'll tell you which folder to put it in.
4. **Freesound**: do you want to create a free API key?
5. **Voice**: which of the 4 samples in `storyforge-data\samples\voices-v3\` do you prefer?

---

## 7. Sources

- NarratoAI: https://github.com/linyqh/NarratoAI · https://www.blog.brightcoding.dev/2025/07/29/narratoai-the-one-click-ai-studio-for-video-commentary-editing-voice-over-subtitles
- FunClip: https://github.com/modelscope/FunClip · https://www.sourcepulse.org/projects/1828172
- CLIP video search: https://docs.vultr.com/semantic-video-frame-search-using-openai-clip-and-vector-database · https://github.com/GughanS/semantic-eye · https://arxiv.org/pdf/2102.12443 · https://www.sourcepulse.org/projects/1861602
- Remotion + Claude Code: https://remotion.dev/docs/ai/claude-code-plugin · https://www.tella.com/blog/how-to-use-remotion-agent-skills-with-claude-code · https://zerofuturetech.substack.com/p/generate-videos-with-claude-code
- MCP video tools: https://mcpservers.org/hi/servers/kyanitelabs/mcp-video · https://enterprisedna.co/directories/mcp/video-creator-ffmpeg-mcp
- Parallax / depth: https://www.huggingface.co/spaces/BrokenSource/DepthFlow/blob/main/README.md · https://pypi.org/project/depthflow · https://waxy.org/category/posts/page/6
- Image-to-video costs: https://www.spheron.network/blog/image-to-video-gpu-cloud-ltx-wan-hunyuan/ · https://docs.ltx.video/api-changelog/2025/10/29
- Archive footage: https://en.wikipedia.org/wiki/Prelinger_Archives · https://blog.archive.org/tag/stock-footage/ · https://pypi.org/project/internetarchive/0.8.5/ · https://publicdomainreview.org/guide-to-finding-interesting-public-domain-works-online/
- Stock sites: https://colorlib.com/wp/best-free-stock-video-sites/ · https://coverr.co/blog/how-to-download-hd-stock-video-free
- Sound: https://rekkerd.org/sonniss-releases-gdc-2026-game-audio-bundle/ · https://zeli.app/en/story/49262841 · https://opensource.creativecommons.org/blog/entries/freesound-intro/
- Fair use and Content ID: https://usethirdchair.com/blog/youtube-fair-use-what-actually-counts-in · https://vidiq.com/es/blog/post/can-you-monetize-anime-clips-recaps-gameplay-youtube/ · https://blog.youtube/news-and-events/updates-to-manual-claiming-policies/ · https://www.foximusic.com/blog/?p=553
- Editing practice: https://djinni.co/jobs/838331-faceless-youtube-video-editor-long-form-illus/ · https://fiverr.com/bilawal_visuals/edit-high-retention-faceless-youtube-documentaries · https://pippit.capcut.com/resource/top-tips-for-building-audience-retention-on-faceless-youtube-channels
- TMDB terms: https://www.themoviedb.org/talk/530525ae9251413488038dc4 · https://api.market/blog/sleeyax/entertainment/best-movie-apis
