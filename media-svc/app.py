"""StoryForge media service: local Kokoro TTS and faster-whisper word alignment (CPU, free)."""

import difflib
import os
import re
import threading
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


@app.get("/health")
def health():
    return {"status": "ok", "whisper_model": WHISPER_MODEL, "models_dir": str(MODELS_DIR)}


@app.post("/tts")
def tts(request: TtsRequest):
    out = inside_data_dir(request.out_path)
    parts = []
    for segment in request.segments:
        if segment.text and segment.text.strip():
            samples, rate = kokoro().create(segment.text.strip(), voice=request.voice, speed=request.speed,
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
