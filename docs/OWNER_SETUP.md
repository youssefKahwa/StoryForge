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

## 2. Music (about 15 min). Recommended.
Without it, videos have voice and sound effects but no music.

1. YouTube Studio → **Audio Library** → Music → filter **License type: No attribution required**.
2. Download 2–3 instrumental tracks per mood into these folders:

| Folder | Mood | Audio Library filter idea |
|---|---|---|
| `C:\Users\anasa\storyforge-data\library\music\dread` | dark, ominous | Mood: Dark, Genre: Ambient / Cinematic |
| `...\tension` | suspense, urgency | Mood: Dramatic |
| `...\mystery` | curious, eerie | Mood: Dark or Calm, Ambient |
| `...\calm` | reflective, quiet | Mood: Calm |
| `...\hope` | warm, uplifting | Mood: Inspirational / Bright |
| `...\sad` | grief, loss | Mood: Sad |
| `...\epic` | triumphant, climax | Mood: Dramatic, Cinematic |

Creative-Commons tracks are fine too: add their credit in `library\music\licenses.json` (see `README.txt` there).
The credit is then added to the video description automatically.

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

## 5. Weekly autopilot (1 min). Optional.
Add topics to **Topics** in the dashboard, then in `.env`:
```
STORYFORGE_AUTOPILOT=true
```
Every Monday at 06:00 the app starts a run from the oldest topic. With YouTube connected, the video is uploaded
privately and you get a Telegram message to review it before it goes public.

## 6. Security housekeeping (2 min). Recommended.
The NVIDIA key and the Cloudflare token were pasted into a chat once. To be safe:
- Cloudflare: *My Profile → API Tokens → Roll* the Workers AI token, then update `CLOUDFLARE_API_TOKEN` in `.env`.
- NVIDIA: the key is no longer used; you can delete it at build.nvidia.com.

---

## Your weekly routine (about 15 minutes of your time)
1. `.\scripts\start.ps1` (opens http://localhost:8080). Glance at **Settings → System check**: a red line tells
   you what to fix; "Cloudflare images: 0 left" means wait for 00:00 UTC (02:00 Morocco summer time) or images get
   reused. Your defaults (voice, length, Shorts, music volume, publish slot) are in **Settings → Preferences**.
2. **New video** → paste 1–3 YouTube links → **Analyze** → pick an angle → pick a voice → **Start**.
3. Come back after 1–2 hours (charger plugged in) → open the run → **Publish kit** tab.
4. Watch the video. Then either upload it with the kit (copy buttons + checklist), or, if YouTube is connected, it's
   already uploaded as private: let it publish, or press **Veto**.
5. Post the Shorts on the following days (titles are in the kit).
6. Every month or so: **Settings → Storage → Free …** deletes the heavy working files of runs older than 14 days.
   Your finished videos, thumbnails and Shorts stay in `storyforge-data\exports`.
