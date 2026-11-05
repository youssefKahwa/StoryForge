"""Human delivery: a scene's narration voiced like a person talking, not a script being read.

A voice engine reads every full stop the same way: the same falling end, the same stop. Measured on our recaps, that
was a stop every 0.9 s of speech. People talk in breath groups, pause by meaning, and never sound metronomic. So:

1. Sentences carry a mood ([mood: x] in the script) and held beats ([beat]); a thought (same mood, no beat between,
   at most 26 words) is voiced in one take so the intonation flows across its sentences.
2. Takes are checked by ear (Whisper): every word there, no extra word, no repeated tail (Magpie sometimes says
   "the hole. hole. hole."). A clean, lively take is kept at once; otherwise more takes are made and the best wins.
3. Each sentence is cut out at the real silence around it, so the pauses are ours, not the punctuation's.
4. Pauses by meaning: short before "and / so / but", a breath after a sentence, longer after a question or a punch
   line, held before a reveal. Commas inside a sentence are tightened. Every value is a little random.
5. Life: a slightly different pace per thought (mood), punch lines a touch slower, a small loudness variation, and a
   soft breath before some sentences that follow a real pause.

The voice itself is a callable (Kokoro or Magpie), so both get the same delivery. Room tone and the mic chain are
added once on the whole narration by the orchestrator."""
import difflib
import random
import re
import subprocess

import numpy as np

CONNECTORS = ("and", "so", "but", "because", "which", "then", "or", "now", "until", "cause", "okay")
SENTENCE = re.compile(r"[^.?!]+(?:\.\.\.|[.?!]+)?")
# Whisper spells some words differently from the script: the same sound, not a mistake by the voice.
SAME = {"grey": "gray", "colour": "color", "favourite": "favorite", "theatre": "theater", "jim": "gym", "ok": "okay",
        "0": "zero", "1": "one", "2": "two", "3": "three", "4": "four", "5": "five", "6": "six", "7": "seven",
        "8": "eight", "9": "nine", "10": "ten", "12": "twelve", "20": "twenty", "100": "hundred"}
MAX_CHUNK_WORDS = 26
FLAT_SEMITONES = 10.0
# Seconds of voice per word above which a take is a repeat (or a stumble); normal speech is about 0.25-0.4.
MAX_VOICED_PER_WORD = 0.52


def norm(word):
    w = re.sub(r"[^\w']", "", word.lower())
    return SAME.get(w, w)


def words_of(text):
    return text.replace("-", " ").split()


# ------------------------------------------------------------------------------------------------- parse and chunk
def units_of(items, mood):
    """Items (text / mood / beat / pause, in script order) -> sentences with their mood and the pause held before."""
    units, buffer, held = [], [], 0

    def flush():
        nonlocal held
        text = " ".join(buffer).replace("*", "")
        buffer.clear()
        for part in SENTENCE.findall(text):
            part = re.sub(r"\s+", " ", part).strip()
            if part and any(c.isalnum() for c in part):
                units.append({"text": part, "mood": mood, "held": held,
                              "end": part[-1] if part[-1] in ".?!," else ","})
                held = 0

    for item in items:
        kind = item.get("kind") or "text"
        if kind == "text":
            if item.get("text"):
                buffer.append(item["text"])
        elif kind == "mood":
            flush()
            mood = (item.get("mood") or item.get("text") or mood).strip().lower()
        elif kind in ("beat", "pause"):
            flush()
            held = max(held, int(item.get("ms") or 0) or 700)
    flush()
    return units


def chunks_of(units):
    chunks = []
    for i, u in enumerate(units):
        n = len(words_of(u["text"]))
        c = chunks[-1] if chunks else None
        if c and u["mood"] == c["mood"] and not u["held"] and c["words"] + n <= MAX_CHUNK_WORDS:
            c["units"].append(i)
            c["words"] += n
        else:
            chunks.append({"mood": u["mood"], "units": [i], "words": n})
    for c in chunks:
        c["text"] = " ".join(units[i]["text"] for i in c["units"])
    return chunks


# ------------------------------------------------------------------------------------------------- judging a take
def pitch_spread(audio16):
    """Pitch range (5-95 %, semitones) of a take: a flat read is a robot read."""
    vals, frame, hop = [], 640, 320
    for s in range(0, len(audio16) - frame, hop):
        x = audio16[s:s + frame]
        if np.sqrt(np.mean(x ** 2)) < 0.02:
            continue
        x = x - x.mean()
        ac = np.correlate(x, x, "full")[frame - 1:]
        k = 53 + int(np.argmax(ac[53:228]))
        if ac[k] > 0.45 * ac[0]:
            vals.append(16000 / k)
    if len(vals) < 10:
        return 0.0
    st = 12 * np.log2(np.array(vals) / np.median(vals))
    return float(np.percentile(st, 95) - np.percentile(st, 5))


def voiced_per_word(audio, sr, words):
    """Seconds of voice (frames within 38 dB of the loudest) per script word."""
    hop = sr // 100
    n = len(audio) // hop
    if not n or not words:
        return 0.0
    rms = np.sqrt(np.mean(audio[:n * hop].reshape(n, hop) ** 2, axis=1) + 1e-12)
    return float((20 * np.log10(rms / (rms.max() + 1e-9)) > -38).sum()) * 0.01 / words


def judge(audio, text, transcribe, sr):
    """How faithful and lively a take is. transcribe(audio16k) -> [(word, start, end)]."""
    t16 = np.arange(0, len(audio) * 16000 / sr) * sr / 16000
    a16 = np.interp(t16, np.arange(len(audio)), audio).astype(np.float32)
    a16 = a16 / (np.abs(a16).max() + 1e-9)
    heard = [(norm(w), s, e) for w, s, e in transcribe(a16) if norm(w)]
    expected = [norm(w) for w in words_of(text)]
    sm = difflib.SequenceMatcher(a=expected, b=[h[0] for h in heard], autojunk=False)
    blocks = [b for b in sm.get_matching_blocks() if b.size]
    matched = sum(b.size for b in blocks)
    last = max((b.b + b.size - 1 for b in blocks), default=-1)
    tail = len(heard) - 1 - last
    subs = sum(min(i2 - i1, j2 - j1) for op, i1, i2, j1, j2 in sm.get_opcodes() if op == "replace")
    missing, extra = len(expected) - matched, len(heard) - matched
    times = [None] * len(expected)
    for b in blocks:
        for k in range(b.size):
            times[b.a + k] = (heard[b.b + k][1], heard[b.b + k][2])
    spread = pitch_spread(a16)
    score = (missing - subs) * 3 + (extra - subs) * 2 + subs + tail * 4 - 0.05 * spread
    clean = missing - subs == 0 and extra - subs == 0 and tail == 0 and subs <= 1
    # A voice that repeats a sentence is not always heard as repeating: Whisper tends to collapse repeated text. Time
    # cannot be fooled: real speech is about 0.3 s of voice per word, a repeat doubles it.
    if len(expected) >= 4 and voiced_per_word(a16, 16000, len(expected)) > MAX_VOICED_PER_WORD:
        score += 25
        clean = False
    return {"audio": audio, "score": score, "clean": clean, "missing": missing - subs, "extra": extra - subs,
            "subs": subs, "tail": tail, "spread": round(spread, 1), "times": times}


def filled(times, duration):
    times = list(times)
    n, i = len(times), 0
    while i < n:
        if times[i] is not None:
            i += 1
            continue
        j = i
        while j < n and times[j] is None:
            j += 1
        a = times[i - 1][1] if i else 0.0
        b = times[j][0] if j < n else duration
        step = max(0.01, (b - a) / (j - i))
        for k in range(i, j):
            times[k] = (a + step * (k - i), a + step * (k - i + 1))
        i = j
    return times


# ------------------------------------------------------------------------------------------------- cutting
def frames_db(audio, sr):
    hop = sr // 100
    n = len(audio) // hop
    if n == 0:
        return np.zeros(0), hop
    rms = np.sqrt(np.mean(audio[:n * hop].reshape(n, hop) ** 2, axis=1) + 1e-12)
    return 20 * np.log10(rms / (rms.max() + 1e-9)), hop


def quiet_run(db, frame, step, need, limit):
    """From frame, walking by step (+1 forward, -1 back), the first run of `need` quiet frames within `limit` frames:
    returns the frame where that run starts (going forward) or ends (going back), or None."""
    run, i = 0, frame
    while 0 <= i < len(db) and abs(i - frame) <= limit:
        run = run + 1 if db[i] < -38 else 0
        if run >= need:
            return i - step * (need - 1)
        i += step
    return None


def cut(audio, start, end, db, hop, sr, safe=False, after=None, until=None):
    """The sentence from start to end (seconds), widened to the real silence around it, with tiny fades.
    A sentence ends at a silence of at least 120 ms: longer than the stop inside a word ("in-st-antly"), shorter than
    the gap a voice leaves before repeating itself. Whisper's time for a last word is often early, so the search goes
    up to a second past it, but never into the next sentence of the take (until) or back into the previous one
    (after): between two sentences without such a silence, the quietest moment is the boundary.
    safe=True (after a failed check) waits for 250 ms of silence and looks further."""
    f0, f1 = int(start * sr / hop), int(end * sr / hop)
    f0, f1 = max(0, min(f0, len(db) - 1)), max(0, min(f1, len(db) - 1))
    need_after, need_before = (25, 10) if safe else (12, 6)
    limit_after, limit_before = (200, 80) if safe else (100, 50)
    stop = int(until * sr / hop) if until is not None else None
    back = int(after * sr / hop) if after is not None else None
    if stop is not None:
        limit_after = max(0, min(limit_after, stop - f1))
    if back is not None:
        limit_before = max(0, min(limit_before, f0 - back))
    hi = quiet_run(db, f1, 1, need_after, limit_after)
    if hi is None:
        if stop is None:
            hi = min(len(db) - 1, f1 + 30)
        elif stop > f1:
            hi = f1 + int(np.argmin(db[f1:stop + 1]))
        else:  # word times touch: the sentence ends at its own last word, never inside the next one
            hi = f1
    lo = quiet_run(db, f0, -1, need_before, limit_before)
    if lo is None:
        if back is None:
            lo = max(0, f0 - 25)
        elif back < f0:
            lo = back + int(np.argmin(db[back:f0 + 1]))
        else:  # word times touch: start at this sentence's own first word, never back in the previous one
            lo = f0
    if stop is not None:
        hi = min(hi, max(stop, f1))
    if back is not None:
        lo = max(lo, min(back, f0))
    s, e = max(0, lo * hop - int(0.02 * sr)), min(len(audio), hi * hop + int(0.04 * sr))
    seg = audio[s:e].copy()
    fade = int(0.006 * sr)
    if len(seg) > 2 * fade:
        seg[:fade] *= np.linspace(0, 1, fade)
        seg[-fade:] *= np.linspace(1, 0, fade)
    return seg


def tighten_commas(seg, sr, rng):
    """A pause inside a sentence (a comma) longer than 260 ms becomes 120-200 ms."""
    db, hop = frames_db(seg, sr)
    quiet = db < -36
    out, pos, i = [], 0, 0
    while i < len(quiet):
        if quiet[i]:
            j = i
            while j < len(quiet) and quiet[j]:
                j += 1
            if (j - i) * hop / sr > 0.26 and i > 0 and j < len(quiet):
                keep = int(rng.uniform(0.12, 0.2) * sr)
                out.append(seg[pos:i * hop + keep // 2])
                pos = j * hop - keep // 2
            i = j
        else:
            i += 1
    out.append(seg[pos:])
    return np.concatenate(out)


def atempo(seg, factor, sr):
    if abs(factor - 1) < 0.004 or len(seg) < sr // 20:
        return seg
    p = subprocess.run(["ffmpeg", "-v", "error", "-f", "f32le", "-ar", str(sr), "-ac", "1", "-i", "-", "-af",
                        f"atempo={factor:.4f}", "-f", "f32le", "-"], input=seg.astype(np.float32).tobytes(),
                       capture_output=True)
    out = np.frombuffer(p.stdout, np.float32)
    return out.copy() if len(out) else seg


# ------------------------------------------------------------------------------------------------- pauses
def pause_after(u, nxt, rng, pace):
    if nxt is None:
        return 0.0
    if nxt["held"]:
        return max(nxt["held"] / 1000.0 * rng.uniform(0.9, 1.15), 0.5)
    if u["end"] == "?":
        p = rng.uniform(0.28, 0.36)
    elif u["end"] == ",":
        p = rng.uniform(0.1, 0.15)
    elif nxt["text"].split()[0].lower().strip(",") in CONNECTORS:
        p = rng.uniform(0.11, 0.17)
    elif len(words_of(u["text"])) <= 4:
        p = rng.uniform(0.2, 0.26)
    else:
        p = rng.uniform(0.18, 0.28)
    if nxt["mood"] != u["mood"]:
        p += 0.06
    if u["mood"] in ("serious", "calm"):
        p *= 1.12
    return p * pace


# ------------------------------------------------------------------------------------------------- speak
def shape(c, units, sr, rng, tempo, safe):
    """Cut the chunk's chosen take into its sentences, each with its own pace and level."""
    audio = c["best"]["audio"]
    times = filled(c["best"]["times"], len(audio) / sr)
    db, hop = frames_db(audio, sr)
    spans, k = [], 0
    for i in c["units"]:
        n = len(words_of(units[i]["text"]))
        spans.append(times[k:k + n] or [(0.0, len(audio) / sr)])
        k += n
    for idx, i in enumerate(c["units"]):
        u, span = units[i], spans[idx]
        n = len(words_of(u["text"]))
        after = spans[idx - 1][-1][1] if idx else None
        until = spans[idx + 1][0][0] if idx + 1 < len(spans) else None
        seg = tighten_commas(cut(audio, span[0][0], span[-1][1], db, hop, sr, safe, after, until), sr, rng)
        factor = tempo.get(u["mood"], 1.0) * (1 + rng.uniform(-0.015, 0.015))
        if n <= 4:
            factor *= 0.97  # a punch line lands a touch slower
        seg = atempo(seg, factor, sr)
        gain = 0.1 / (np.sqrt(np.mean(seg ** 2)) + 1e-9) * 10 ** (rng.uniform(-1, 1) / 20)
        if n <= 4:
            gain *= 10 ** (1 / 20)
        u["audio"] = (seg * gain).astype(np.float32)


def assemble(units, sr, rng, pace, breath):
    """The sentences with pauses by meaning and, now and then, a soft breath before one."""
    parts, t, last_breath = [], 0.0, -10.0
    for i, u in enumerate(units):
        if i:
            p = pause_after(units[i - 1], u, rng, pace)
            if (breath is not None and p >= 0.3 and len(words_of(u["text"])) > 3 and t - last_breath > 6
                    and rng.random() < 0.45):
                b = breath * 0.06 * np.abs(u["audio"]).max()
                lead = max(0.0, p - len(b) / sr - 0.03)
                parts += [np.zeros(int(lead * sr), np.float32), b.astype(np.float32),
                          np.zeros(int(0.03 * sr), np.float32)]
                t += lead + len(b) / sr + 0.03
                last_breath = t
            else:
                parts.append(np.zeros(int(p * sr), np.float32))
                t += p
        parts.append(u["audio"])
        t += len(u["audio"]) / sr
    voice = np.concatenate(parts)
    return (voice / (np.abs(voice).max() + 1e-9) * 0.85).astype(np.float32)


def speak(items, mood, synth, transcribe, *, sr, tempo, takes=3, pace=1.0, breath=None, seed="", log=None):
    """Voice a scene. synth(text, mood, attempt) -> float32 audio at sr; tempo: {mood: factor}.
    Returns (audio, report). The finished scene is checked by ear once more: if a word went missing in the cutting,
    the scene is cut again in safe mode (same takes, more generous cuts)."""
    units = units_of(items, mood)
    if not units:
        return np.zeros(int(0.2 * sr), np.float32), {"chunks": 0, "takes": 0, "issues": []}
    chunks = chunks_of(units)
    report = {"chunks": len(chunks), "takes": 0, "issues": []}
    for c in chunks:
        candidates = []
        for attempt in range(max(1, takes)):
            audio = synth(c["text"], c["mood"], attempt)
            report["takes"] += 1
            j = judge(audio, c["text"], transcribe, sr)
            candidates.append(j)
            if j["clean"] and (j["spread"] >= FLAT_SEMITONES or j["spread"] == 0):
                break
        c["best"] = best = min(candidates, key=lambda j: j["score"])
        inner_extra = best["extra"] - best["tail"]
        if best["missing"] or inner_extra > 0:
            report["issues"].append(f"\"{c['text'][:60]}\": missing {best['missing']}, extra {inner_extra}")
        elif best["tail"]:
            report["trimmed"] = report.get("trimmed", 0) + 1  # a repeated ending, cut off by the cut below
    text = " ".join(u["text"] for u in units)
    lost_in_takes = sum(c["best"]["missing"] for c in chunks)
    voice = None
    for safe in (False, True):
        rng = random.Random(seed or "storyforge")
        for c in chunks:
            shape(c, units, sr, rng, tempo, safe)
        voice = assemble(units, sr, rng, pace, breath)
        final = judge(voice, text, transcribe, sr)
        if final["missing"] <= lost_in_takes:
            break
        report["recut"] = True  # a word was lost in the cutting: cut again, more generously
    if final["missing"] > lost_in_takes:
        report["issues"].append(f"{final['missing'] - lost_in_takes} word(s) may be clipped")
    voiced = voiced_per_word(voice, sr, len(words_of(text)))
    if voiced > MAX_VOICED_PER_WORD:  # an alarm: something was said twice
        report["issues"].append(f"{voiced:.2f} s of voice per word: part of the scene may be repeated")
    if log and report["issues"]:
        log("; ".join(report["issues"]))
    return voice, report
