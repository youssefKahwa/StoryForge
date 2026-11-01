"""StoryForge media service: local Kokoro TTS and faster-whisper word alignment (CPU, free)."""

import difflib
import os
import re
import threading
import zlib
from pathlib import Path

import numpy as np
import soundfile as sf
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

MODELS_DIR = Path(os.environ.get("STORYFORGE_MODELS_DIR", Path.home() / "storyforge-data" / "models"))
DATA_DIR = Path(os.environ.get("STORYFORGE_DATA_DIR", Path.home() / "storyforge-data")).resolve()
WHISPER_MODEL = os.environ.get("WHISPER_MODEL", "base.en")
WHISPER_COMPUTE = os.environ.get("WHISPER_COMPUTE", "int8")
SAMPLE_RATE = 24_000

app = FastAPI(title="StoryForge media-svc")
_lock = threading.Lock()
_kokoro = None
_whisper = None


def voice_style(voice: str):
    """A Kokoro voice name, or a blend like "am_michael:60+am_fenrir:40" (weights in percent): the channel's own
    voice, made by mixing the style vectors of several stock voices."""
    if "+" not in voice and ":" not in voice:
        return voice
    parts = []
    for item in voice.split("+"):
        name, _, weight = item.strip().partition(":")
        parts.append((name.strip(), float(weight) if weight else 1.0))
    total = sum(w for _, w in parts) or 1.0
    style = None
    for name, weight in parts:
        vector = kokoro().get_voice_style(name) * (weight / total)
        style = vector if style is None else style + vector
    return style


def kokoro():
    global _kokoro
    with _lock:
        if _kokoro is None:
            from kokoro_onnx import Kokoro
            base = MODELS_DIR / "kokoro"
            _kokoro = Kokoro(str(base / "kokoro-v1.0.onnx"), str(base / "voices-v1.0.bin"))
        return _kokoro


def whisper():
    global _whisper
    with _lock:
        if _whisper is None:
            from faster_whisper import WhisperModel
            _whisper = WhisperModel(WHISPER_MODEL, device="cpu", compute_type=WHISPER_COMPUTE,
                                    download_root=str(MODELS_DIR / "whisper"))
        return _whisper


def inside_data_dir(path: str) -> Path:
    resolved = Path(path).resolve()
    if DATA_DIR not in resolved.parents:
        raise HTTPException(status_code=400, detail=f"path must be inside {DATA_DIR}")
    return resolved


class Segment(BaseModel):
    text: str | None = None
    pause_ms: int = 0


class TtsRequest(BaseModel):
    segments: list[Segment]
    voice: str = "af_heart"
    speed: float = Field(1.0, ge=0.5, le=2.0)
    lang: str = "en-us"
    out_path: str


class AlignRequest(BaseModel):
    wav_path: str
    text: str


class TranscriptRequest(BaseModel):
    url: str


YOUTUBE_URL = re.compile(r"^https?://(www\.|m\.)?(youtube\.com/(watch\?|shorts/|live/)|youtu\.be/)")
_whisper_multi = None


def whisper_multi():
    global _whisper_multi
    with _lock:
        if _whisper_multi is None:
            from faster_whisper import WhisperModel
            _whisper_multi = WhisperModel(os.environ.get("WHISPER_MULTI_MODEL", "small"), device="cpu",
                                          compute_type=WHISPER_COMPUTE, download_root=str(MODELS_DIR / "whisper"))
        return _whisper_multi


def caption_track(info: dict):
    """Prefer captions in the video's own language: manual first, then YouTube's original-language ASR."""
    manual, auto = info.get("subtitles") or {}, info.get("automatic_captions") or {}
    lang = (info.get("language") or "").split("-")[0]
    candidates = []
    if lang:
        candidates += [("manual", manual.get(k)) for k in manual if k.split("-")[0] == lang]
        candidates += [("auto", auto.get(f"{lang}-orig")), ("auto", auto.get(lang))]
    candidates += [("manual", v) for v in manual.values()]
    candidates += [("auto", v) for k, v in auto.items() if k.endswith("-orig")]
    for source, tracks in candidates:
        json3 = next((t for t in tracks or [] if t.get("ext") == "json3"), None)
        if json3:
            return source, json3["url"]
    return None, None


def json3_text(url: str) -> str:
    import json
    import urllib.request
    with urllib.request.urlopen(url, timeout=60) as response:
        data = json.load(response)
    text = " ".join(seg.get("utf8", "") for event in data.get("events", []) for seg in (event.get("segs") or []))
    return " ".join(text.split())


def whisper_transcript(info: dict, url: str) -> tuple[str, str]:
    import subprocess
    import tempfile
    import yt_dlp
    work = Path(tempfile.mkdtemp(prefix="sf-insp-", dir=DATA_DIR))
    try:
        opts = {"format": "bestaudio/best", "quiet": True, "no_warnings": True,
                "outtmpl": str(work / "audio.%(ext)s")}
        with yt_dlp.YoutubeDL(opts) as ydl:
            ydl.download([url])
        audio = next(work.glob("audio.*"))
        wav = work / "audio16k.wav"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(audio), "-ac", "1", "-ar", "16000", str(wav)],
                       check=True)
        samples, _ = sf.read(str(wav), dtype="float32")
        segments, detected = whisper_multi().transcribe(samples, beam_size=1, vad_filter=True)
        return " ".join(s.text.strip() for s in segments), detected.language
    finally:
        for f in work.glob("*"):
            f.unlink(missing_ok=True)
        work.rmdir()


@app.post("/transcript")
def transcript(request: TranscriptRequest):
    if not YOUTUBE_URL.match(request.url):
        raise HTTPException(status_code=400, detail="only YouTube video URLs are supported")
    import yt_dlp
    try:
        with yt_dlp.YoutubeDL({"skip_download": True, "quiet": True, "no_warnings": True}) as ydl:
            info = ydl.extract_info(request.url, download=False)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"YouTube refused the request: {e}") from e
    source, track = caption_track(info)
    language = info.get("language")
    if track:
        text = json3_text(track)
    else:
        text, language = whisper_transcript(info, request.url)
        source = "whisper"
    return {"video_id": info.get("id"), "title": info.get("title"), "channel": info.get("channel"),
            "duration_sec": info.get("duration"), "language": language, "source": source, "transcript": text}


class TrailerRequest(BaseModel):
    query: str
    out_dir: str
    work: str | None = None
    max_results: int = 8


NOT_A_SINGLE_WORK = ("films of", "fest", "festival", "retrospective", "collection", "compilation", "marathon",
                     "tribute", "every ", "ranking", "top 10", "reaction", "fan made", "fan-made", "parody", "recap")


def _norm(text: str) -> str:
    return " " + re.sub(r"[^a-z0-9]+", " ", (text or "").lower()).strip() + " "


@app.post("/trailer")
def trailer(request: TrailerRequest):
    """Finds the official trailer of a film/series and downloads its picture only (no audio track)."""
    out_dir = inside_data_dir(request.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    import yt_dlp
    try:
        with yt_dlp.YoutubeDL({"quiet": True, "no_warnings": True, "skip_download": True,
                               "extract_flat": True}) as ydl:
            found = ydl.extract_info(f"ytsearch{max(1, min(request.max_results, 15))}:{request.query}",
                                     download=False)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"YouTube search failed: {e}") from e
    candidates = []
    work = _norm(request.work) if request.work else None
    for entry in found.get("entries") or []:
        title = (entry.get("title") or "").lower()
        duration = entry.get("duration") or 0
        if "trailer" not in title or not 40 <= duration <= 300:
            continue
        # Only the work's own trailer: its full title must be there, and no festival/retrospective/compilation reels.
        if (work and work not in _norm(title)) or any(word in title for word in NOT_A_SINGLE_WORK):
            continue
        score = (2 if "official" in title else 0) + (1 if entry.get("channel_is_verified") else 0)
        candidates.append((score, entry))
    if not candidates:
        raise HTTPException(status_code=404, detail="no official trailer found")
    candidates.sort(key=lambda c: -c[0])
    entry = candidates[0][1]
    url = entry.get("url") or f"https://www.youtube.com/watch?v={entry['id']}"
    options = {"quiet": True, "no_warnings": True, "noplaylist": True,
               "format": "bv*[height<=1080][ext=mp4]/bv*[height<=1080]",
               "outtmpl": str(out_dir / "%(id)s.%(ext)s")}
    try:
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(url, download=True)
            path = ydl.prepare_filename(info)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"trailer download failed: {e}") from e
    return {"file": path, "video_id": info.get("id"), "title": info.get("title"), "channel": info.get("channel"),
            "url": info.get("webpage_url"), "duration_sec": info.get("duration")}


class FootageRequest(BaseModel):
    query: str
    out_dir: str
    work: str
    max_videos: int = 3
    skip_ids: list[str] = []


# Uploads that are not the work itself (reviews, reactions, edits) are never used as footage.
NOT_FOOTAGE = NOT_A_SINGLE_WORK + ("review", "explained", "ending explained", "analysis", "breakdown", "theory",
                                   "amv", "edit ", "tiktok", "#shorts", "podcast", "interview", "reacts",
                                   "behind the scenes", "making of", "honest trailer", "pitch meeting", "everything wrong")


@app.post("/footage")
def footage(request: FootageRequest):
    """Real footage of a film/series/anime for commentary: trailers, teasers, TV spots, official clips and scene
    uploads whose title names the work. Picture only (no audio track), at most 1080p, 20 s to 12 min each."""
    out_dir = inside_data_dir(request.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    import yt_dlp
    try:
        with yt_dlp.YoutubeDL({"quiet": True, "no_warnings": True, "skip_download": True,
                               "extract_flat": True}) as ydl:
            found = ydl.extract_info(f"ytsearch15:{request.query}", download=False)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"YouTube search failed: {e}") from e
    work = _norm(request.work)
    picked = []
    for entry in found.get("entries") or []:
        title = (entry.get("title") or "").lower()
        duration = entry.get("duration") or 0
        if entry.get("id") in request.skip_ids or not 20 <= duration <= 720:
            continue
        if work not in _norm(title) or any(word in title for word in NOT_FOOTAGE):
            continue
        picked.append(entry)
        if len(picked) >= max(1, min(request.max_videos, 6)):
            break
    videos = []
    options = {"quiet": True, "no_warnings": True, "noplaylist": True,
               "format": "bv*[height<=1080][ext=mp4]/bv*[height<=1080]",
               "outtmpl": str(out_dir / "%(id)s.%(ext)s")}
    for entry in picked:
        url = entry.get("url") or f"https://www.youtube.com/watch?v={entry['id']}"
        try:
            with yt_dlp.YoutubeDL(options) as ydl:
                info = ydl.extract_info(url, download=True)
                path = ydl.prepare_filename(info)
        except Exception as e:  # one refused video must not stop the others
            print(f"footage download failed for {url}: {e}", flush=True)
            continue
        videos.append({"file": path, "video_id": info.get("id"), "title": info.get("title"),
                       "channel": info.get("channel"), "url": info.get("webpage_url"),
                       "duration_sec": info.get("duration")})
    return {"videos": videos}


SFX_RATE = 48_000


class SfxRequest(BaseModel):
    name: str
    out_path: str


def _envelope(n: int, attack: float, release: float) -> np.ndarray:
    env = np.ones(n, dtype=np.float32)
    a, r = max(1, int(n * attack)), max(1, int(n * release))
    env[:a] = np.linspace(0, 1, a)
    env[-r:] = np.linspace(1, 0, r) ** 2
    return env


def _lowpass(signal: np.ndarray, alpha: np.ndarray | float) -> np.ndarray:
    out = np.empty_like(signal)
    acc = 0.0
    alphas = np.broadcast_to(alpha, signal.shape)
    for i in range(len(signal)):
        acc += alphas[i] * (signal[i] - acc)
        out[i] = acc
    return out


def synth(name: str) -> np.ndarray:
    rng = np.random.default_rng(zlib.crc32(name.encode()))
    if name == "whoosh":
        n = int(SFX_RATE * 0.9)
        sweep = np.linspace(0.02, 0.35, n) ** 1.5
        sweep = np.concatenate([sweep[: n // 2], sweep[: n // 2][::-1], sweep[-1:]])[:n]
        sig = _lowpass(rng.standard_normal(n).astype(np.float32), sweep) * _envelope(n, 0.45, 0.5)
    elif name == "low_hit":
        n = int(SFX_RATE * 2.2)
        t = np.arange(n) / SFX_RATE
        freq = 55 * np.exp(-t * 1.2) + 30
        tone = np.sin(2 * np.pi * np.cumsum(freq) / SFX_RATE)
        thump = _lowpass(rng.standard_normal(n).astype(np.float32), 0.02) * np.exp(-t * 18) * 6
        sig = (tone * np.exp(-t * 2.2) + thump).astype(np.float32)
    elif name == "riser":
        n = int(SFX_RATE * 3.0)
        t = np.arange(n) / SFX_RATE
        tone = np.sin(2 * np.pi * np.cumsum(np.linspace(110, 440, n)) / SFX_RATE) * 0.4
        noise = _lowpass(rng.standard_normal(n).astype(np.float32), np.linspace(0.01, 0.3, n))
        sig = ((tone + noise) * (t / t[-1]) ** 2 * _envelope(n, 0.02, 0.04)).astype(np.float32)
    elif name == "wind":
        n = int(SFX_RATE * 8.0)
        brown = np.cumsum(rng.standard_normal(n)).astype(np.float32)
        brown -= _lowpass(brown, 0.0005)
        gust = 0.6 + 0.4 * np.sin(2 * np.pi * np.arange(n) / SFX_RATE * 0.23)
        sig = brown * gust.astype(np.float32) * _envelope(n, 0.2, 0.25)
    elif name == "tick":
        n = int(SFX_RATE * 0.12)
        t = np.arange(n) / SFX_RATE
        sig = (np.sin(2 * np.pi * 2400 * t) * np.exp(-t * 120)).astype(np.float32)
    elif name == "paper":
        n = int(SFX_RATE * 0.7)
        crackle = rng.standard_normal(n).astype(np.float32) * (rng.random(n) > 0.82)
        sig = _lowpass(crackle, 0.45) * _envelope(n, 0.1, 0.5)
    elif name in ("whip", "zoom_in", "swoosh_soft"):
        # Transition swishes: band-limited noise whose brightness peaks at the cut, panned across the stereo field.
        length = {"whip": 0.42, "zoom_in": 0.85, "swoosh_soft": 0.65}[name]
        n = int(SFX_RATE * length)
        x = np.linspace(0, 1, n)
        peak_at = {"whip": 0.55, "zoom_in": 0.8, "swoosh_soft": 0.5}[name]
        bright = np.exp(-((x - peak_at) ** 2) / (2 * (0.16 if name != "whip" else 0.1) ** 2))
        noise = rng.standard_normal(n).astype(np.float32)
        body = _lowpass(noise, 0.015 + 0.42 * bright) - 0.6 * _lowpass(noise, 0.004)
        sig = (body * bright).astype(np.float32)
        if name == "zoom_in":
            t = np.arange(n) / SFX_RATE
            thud_start = int(n * peak_at)
            thud = np.zeros(n, dtype=np.float32)
            tt = t[: n - thud_start]
            thud[thud_start:] = np.sin(2 * np.pi * (70 * tt - 20 * tt ** 2)) * np.exp(-tt * 14) * 0.9
            sig = sig + thud
        pan = {"whip": np.linspace(-0.9, 0.9, n), "zoom_in": np.zeros(n),
               "swoosh_soft": np.linspace(0.6, -0.6, n)}[name]
        peak = float(np.max(np.abs(sig))) or 1.0
        mono = sig / peak * 0.7
        left = mono * np.sqrt(0.5 * (1 - pan))
        right = mono * np.sqrt(0.5 * (1 + pan))
        return np.stack([left, right], axis=1).astype(np.float32) * 1.41
    elif name == "flash_hit":
        n = int(SFX_RATE * 1.4)
        t = np.arange(n) / SFX_RATE
        crack = _lowpass(rng.standard_normal(n).astype(np.float32), 0.6) * np.exp(-t * 45)
        sub = np.sin(2 * np.pi * (48 * t - 6 * t ** 2)) * np.exp(-t * 3.2)
        shimmer = _lowpass(rng.standard_normal(n).astype(np.float32), 0.25) * np.exp(-t * 6) * 0.25
        sig = (crack * 0.8 + sub + shimmer).astype(np.float32)
    elif name == "shutter":
        # Camera shutter: two mechanical clicks 50 ms apart and a tiny body thump (the "frame switch" click).
        n = int(SFX_RATE * 0.2)
        t = np.arange(n) / SFX_RATE
        noise = rng.standard_normal(n).astype(np.float32)
        click = noise - _lowpass(noise, 0.08)
        sig = np.zeros(n, dtype=np.float32)
        for start, level in ((0.0, 1.0), (0.05, 0.7)):
            s0 = int(start * SFX_RATE)
            tt = t[: n - s0]
            sig[s0:] += click[: n - s0] * np.exp(-tt * 260).astype(np.float32) * level
        sig += (np.sin(2 * np.pi * 180 * t) * np.exp(-t * 60) * 0.25).astype(np.float32)
    elif name == "glitch":
        # Digital stutter: short square-wave and crushed-noise bursts jumping between the speakers.
        n = int(SFX_RATE * 0.38)
        left = np.zeros(n, dtype=np.float32)
        right = np.zeros(n, dtype=np.float32)
        pos = 0
        while pos < n:
            length = int(SFX_RATE * rng.uniform(0.012, 0.045))
            end = min(n, pos + length)
            tt = np.arange(end - pos) / SFX_RATE
            if rng.random() < 0.5:
                burst = np.sign(np.sin(2 * np.pi * rng.uniform(180, 1800) * tt)) * 0.6
            else:
                burst = np.round(rng.standard_normal(end - pos) * 3) / 3 * 0.5
            if rng.random() < 0.25:
                burst = burst * 0.1
            pan = rng.uniform(-1, 1)
            left[pos:end] = burst * np.sqrt(0.5 * (1 - pan))
            right[pos:end] = burst * np.sqrt(0.5 * (1 + pan))
            pos = end
        env = _envelope(n, 0.02, 0.2)
        stereo = np.stack([left * env, right * env], axis=1)
        peak = float(np.max(np.abs(stereo))) or 1.0
        return (stereo / peak * 0.7).astype(np.float32)
    elif name == "boom":
        # Cinematic impact: a crack, a deep falling body and a long dark tail.
        n = int(SFX_RATE * 2.6)
        t = np.arange(n) / SFX_RATE
        freq = 38 + 34 * np.exp(-t * 4)
        body = np.sin(2 * np.pi * np.cumsum(freq) / SFX_RATE) * np.exp(-t * 1.6)
        crack = _lowpass(rng.standard_normal(n).astype(np.float32), 0.5) * np.exp(-t * 38) * 0.9
        tail = _lowpass(rng.standard_normal(n).astype(np.float32), 0.01) * np.exp(-t * 2.2) * 4
        sig = (body + crack + tail).astype(np.float32)
    elif name == "sub_drop":
        # Sub drop: a sine falling from 110 Hz to about 28 Hz, the bass "drop" under a reveal.
        n = int(SFX_RATE * 1.8)
        t = np.arange(n) / SFX_RATE
        freq = 28 + 82 * np.exp(-t * 2.6)
        sig = (np.sin(2 * np.pi * np.cumsum(freq) / SFX_RATE) * np.exp(-t * 1.4)
               * np.minimum(1, t / 0.01)).astype(np.float32)
    elif name == "film_burn":
        # Rising whoosh with projector crackle that lands on the cut (peak at 0.45 s), then a soft hit.
        n = int(SFX_RATE * 1.3)
        t = np.arange(n) / SFX_RATE
        peak_at = 0.45
        bright = np.exp(-((t - peak_at) ** 2) / (2 * 0.12 ** 2))
        noise = rng.standard_normal(n).astype(np.float32)
        whoosh = _lowpass(noise, 0.02 + 0.4 * bright) * bright
        crackle = rng.standard_normal(n).astype(np.float32) * (rng.random(n) > 0.97) * np.exp(-np.abs(t - 0.6) * 3) * 0.6
        hit_t = np.clip(t - peak_at, 0, None)
        hit = np.sin(2 * np.pi * (60 * hit_t - 10 * hit_t ** 2)) * np.exp(-hit_t * 7) * (t >= peak_at) * 0.8
        sig = (whoosh + crackle + hit).astype(np.float32)
    else:
        raise HTTPException(status_code=400, detail=f"unknown sfx {name}")
    peak = float(np.max(np.abs(sig))) or 1.0
    mono = sig / peak * 0.7
    return np.stack([mono, mono], axis=1)


@app.post("/sfx")
def sfx(request: SfxRequest):
    out = inside_data_dir(request.out_path)
    if not out.exists():
        out.parent.mkdir(parents=True, exist_ok=True)
        sf.write(str(out), synth(request.name), SFX_RATE, subtype="PCM_16")
    info = sf.info(str(out))
    return {"duration_sec": info.duration, "sample_rate": info.samplerate}


@app.get("/health")
def health():
    return {"status": "ok", "whisper_model": WHISPER_MODEL, "models_dir": str(MODELS_DIR)}


@app.post("/tts")
def tts(request: TtsRequest):
    out = inside_data_dir(request.out_path)
    parts = []
    for segment in request.segments:
        if segment.text and segment.text.strip():
            samples, rate = kokoro().create(segment.text.strip(), voice=voice_style(request.voice), speed=request.speed,
                                            lang=request.lang)
            if rate != SAMPLE_RATE:
                raise HTTPException(status_code=500, detail=f"unexpected sample rate {rate}")
            parts.append(np.asarray(samples, dtype=np.float32))
        elif segment.pause_ms > 0:
            parts.append(np.zeros(int(SAMPLE_RATE * segment.pause_ms / 1000), dtype=np.float32))
    audio = np.concatenate(parts) if parts else np.zeros(1, dtype=np.float32)
    out.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(out), np.clip(audio, -1.0, 1.0), SAMPLE_RATE, subtype="PCM_16")
    return {"duration_sec": len(audio) / SAMPLE_RATE, "sample_rate": SAMPLE_RATE}


def norm(token: str) -> str:
    return re.sub(r"[^\w']", "", token.lower().replace("’", "'"))


def load_16k(path: Path) -> np.ndarray:
    """Decode with soundfile and resample to Whisper's 16 kHz, avoiding PyAV version conflicts."""
    audio, rate = sf.read(str(path), dtype="float32", always_2d=True)
    mono = audio.mean(axis=1)
    if rate == 16_000:
        return mono
    target = np.arange(0, len(mono) * 16_000 / rate) * rate / 16_000
    return np.interp(target, np.arange(len(mono)), mono).astype(np.float32)


def fill_gaps(times: list, duration: float) -> None:
    n, i = len(times), 0
    while i < n:
        if times[i] is not None:
            i += 1
            continue
        j = i
        while j < n and times[j] is None:
            j += 1
        start = times[i - 1][1] if i > 0 else 0.0
        end = max(start, times[j][0] if j < n else duration)
        step = (end - start) / (j - i)
        for k in range(i, j):
            times[k] = (start + step * (k - i), start + step * (k - i + 1))
        i = j


@app.post("/align")
def align(request: AlignRequest):
    wav = inside_data_dir(request.wav_path)
    segments, info = whisper().transcribe(load_16k(wav), language="en", word_timestamps=True, beam_size=1,
                                          vad_filter=False, condition_on_previous_text=False)
    heard = [w for s in segments for w in (s.words or [])]
    script = request.text.split()
    script_norm = [norm(t) for t in script]
    heard_norm = [norm(w.word) for w in heard]

    times: list[tuple[float, float] | None] = [None] * len(script)
    matcher = difflib.SequenceMatcher(a=script_norm, b=heard_norm, autojunk=False)
    for block in matcher.get_matching_blocks():
        for k in range(block.size):
            w = heard[block.b + k]
            times[block.a + k] = (float(w.start), float(w.end))
    edits = sum(max(i2 - i1, j2 - j1) for op, i1, i2, j1, j2 in matcher.get_opcodes() if op != "equal")
    wer = edits / max(1, len(script))

    duration = float(info.duration)
    fill_gaps(times, duration)
    words = [{"text": token, "start": round(times[i][0], 3), "end": round(times[i][1], 3)}
             for i, token in enumerate(script)]
    return {"words": words, "transcript": " ".join(w.word.strip() for w in heard), "wer": round(wer, 4),
            "duration_sec": duration}
