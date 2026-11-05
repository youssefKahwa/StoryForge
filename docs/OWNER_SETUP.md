# Owner setup: the only things StoryForge needs from you

Everything else is automatic. Do these once. Each step says how long it takes and what you get.
Put every secret in `C:\Users\anasa\OneDrive\Desktop\StoryForge\.env` (never in `.env.example`, never in chat).

---

## 1. Windows power settings (2 min). Required.
A run takes 1–2 hours of CPU. On battery Windows slows it about 10×, and sleep pauses it.
The app already blocks sleep while a run is active, but closing the lid can still suspend it.

- Keep the charger plugged in during a run.
- Settings → System → Power & battery → **Power mode: Best performance**.
- Control Panel → Hardware and Sound → Power Options → *Choose what closing the lid does* →
  **When plugged in: Do nothing**, if you close the lid during runs.

## 2. Music: done (2026-10-19)
21 instrumental tracks by Kevin MacLeod (incompetech.com), 3 per mood, are installed in
`C:\Users\anasa\storyforge-data\library\music\<mood>`. License: Creative Commons Attribution 4.0, so they're free
for monetized YouTube videos as long as they're credited. The app adds the credit lines to the description by itself (section "Music").
Each track's ISRC and download URL are in `library\music\MUSIC_CREDITS.txt`, in case a Content ID claim ever shows up
(dispute it with the ISRC and the CC BY 4.0 license).

To add your own tracks later: drop them into a mood folder. Tracks without an entry in `licenses.json` are treated
as YouTube Audio Library "No attribution required" downloads, so only add tracks of that kind that way.

## 3. YouTube upload (about 15 min). Optional.
Without it, every run still produces the full Publish Kit and you upload by hand.

1. **Verify the channel by phone**: https://www.youtube.com/verify. This is required for videos longer than
   15 minutes and for custom thumbnails.
2. https://console.cloud.google.com → create a project named **StoryForge**.
3. *APIs & Services → Library* → search **YouTube Data API v3** → **Enable**.
4. *Google Auth Platform* (OAuth consent screen):
   - Branding: app name **StoryForge**, your email.
   - Audience: **External**, then **Publish app → In production**. This is important: in "Testing" mode Google
     logs you out every 7 days, which breaks the weekly runs. Verification is not needed for personal use.
   - Data access: add the scopes `.../auth/youtube.upload` and `.../auth/youtube.force-ssl`.
5. *Clients → Create client → Application type: **Desktop app*** → name **StoryForge** → Create.
6. Put the two values in `.env`:
   ```
   GOOGLE_CLIENT_ID=....apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=...
   STORYFORGE_PUBLISH=youtube
   ```
7. Restart the app → Dashboard → **Settings → Connect YouTube** → pick your channel.
   Google shows "Google hasn't verified this app": click **Advanced → Go to StoryForge**, then **Allow**.
8. From then on each run uploads the video **as private**, schedules it for the best slot, sets the thumbnail and
   captions, and you have until the publish time to press **Veto** in the dashboard.

## 4. Phone notifications with Telegram (5 min). Optional.
1. In Telegram, open **@BotFather** → `/newbot` → follow the steps → copy the **token**.
2. Send any message to your new bot.
3. Open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser and copy `"chat":{"id": ...}`.
4. Put them in `.env`:
   ```
   TELEGRAM_BOT_TOKEN=...
   TELEGRAM_CHAT_ID=...
   ```
5. Restart → **Settings → Send test notification**.

## 4b. Real stock footage: done (2026-10-20, Pixabay key in `.env`)
Real photos and some videos (Wikimedia Commons) work without any key. For many more real video clips (sea, storms,
fog, forests, cities...):
1. Create a free account at https://pixabay.com and open https://pixabay.com/api/docs/ while logged in: your key
   is shown in the "Parameters" table (`key`).
2. Put it in `.env`: `PIXABAY_API_KEY=...` and restart StoryForge.
Settings → System check then shows "Real photos and footage: OK". (Pexels keys are paused; if you ever get one,
`PEXELS_API_KEY=` works too.)

Fair-use trailer clips: Settings → Preferences → "Fair-use trailer clips" (on). For stories about a film, series
or anime the app picks a few short, muted moments from the official trailer. Before publishing, watch them (QA
lists each one). A Content ID claim can still happen; it usually only moves that video's ad money to the studio.

Your talking logo: `assets/channel/DownRightTalkingMouth.mp4` is used automatically (Settings → Preferences →
"Talking logo host" to switch it off). Replace the file with another logo video of the same layout to change it.

## 5. How the app runs: only when you open it
StoryForge is not a server that stays on. You start it when you want a video (once a week, or 2–3 times), give the
input, wait until the video is done, review it, then close the window. Nothing runs while it is closed.
- Closed it (or the PC restarted) in the middle of a video? Start it again: the video continues from the step
  where it stopped.
- With YouTube connected, the video is uploaded as private with a publish date; YouTube publishes it at that time
  even when StoryForge is closed. To cancel, press **Veto** in the app, or set it to Private in YouTube Studio.
- Telegram messages only arrive while the app is open (for example "your video is ready").
- Rendering speed: keep the charger plugged in and close Chrome/other heavy apps while a video renders. The app
  picks how many render tabs fit in the free memory (about 1 GB each) and uses only 2 on battery, so it will not
  crash, but with 2 GB free a 12-minute video renders several times slower than with 8 GB free.
- Leave `STORYFORGE_AUTOPILOT=false` (the default): the weekly timer only works if the app stays on all week.

## 6. Security housekeeping (2 min). Recommended.
The NVIDIA key and the Cloudflare token were pasted into a chat once. To be safe:
- Cloudflare (2 min): dash.cloudflare.com → *My Profile → API Tokens* → your Workers AI token → **Roll** → copy the
  new value, then run `.\scripts\set-cloudflare-token.ps1` and paste it at the hidden prompt (it checks the token
  and saves it to `.env`). Don't paste it into a chat. The app can't roll it itself: the token is limited to
  Workers AI on purpose.
- NVIDIA: the key is no longer used; you can delete it at build.nvidia.com.

---

## Your weekly routine (about 10 minutes of your time)

One time: write your **channel brief** in Settings → Preferences (niche, audience, tone). Quick start uses it to
pick a topic when you give no input.
1. `.\scripts\start.ps1` (opens http://localhost:8080). Glance at **Settings → System check**: a red line tells
   you what to fix; "Cloudflare images: 0 left" means wait for 00:00 UTC (02:00 Morocco summer time) or images get
   reused. Your defaults (voice, length, Shorts, music volume, publish slot) are in **Settings → Preferences**.
2. **New video** → **Quick start**: paste 1–3 YouTube links (or just a topic, or nothing: the app takes the next
   backlog topic and asks the AI for new ideas when the backlog is empty) → **Make my video**. The angle, voice,
   length and publish slot are chosen for you. (The step-by-step wizard below it is still there.)
3. Come back after 1–2 hours (charger plugged in) → open the run → **Publish kit** tab.
4. Watch the video in the **Revise** tab. Something off? Press **Mark this moment** (or click a picture in the strip),
   choose Picture / Narration / Music / Title & thumbnail, write a short note, add as many notes as you like, then
   **Apply**. Only what you pointed at is redone (same voice clips and images elsewhere), and the video is rendered
   again. When you're happy, either upload it with the kit (copy buttons + checklist), or, if YouTube is connected, it's
   already uploaded as private: let it publish, or press **Veto**.
5. Post the Shorts on the following days (titles are in the kit).
6. Every month or so: **Settings → Storage → Free …** deletes the heavy working files of runs older than 14 days.
   Your finished videos, thumbnails and Shorts stay in `storyforge-data\exports`.
