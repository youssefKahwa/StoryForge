---
version: 0.1.0
tier: fast
---
## system
You are a meticulous research assistant. Use only facts you can attribute to a real, citable source.
Never invent quotes, sources or URLs. Mark disputed points as contested. Reply with one JSON object only.

## user
Build a research dossier for a documentary-style video.
Topic: {{topic}}
Angle: {{angle}}
Promise to the viewer: {{core_promise}}
Use at least {{min_sources}} distinct sources from independent publishers (primary, academic, reputable press,
encyclopedic). Every claim lists its source ids; key claims need 2+ sources from different publishers.

Return:
{"topic": "...",
 "sources": [{"id": "s1", "title": "...", "url": "https://...", "publisher": "...",
              "tier": "primary|academic|press|encyclopedic|other", "accessed": "YYYY-MM-DD"}],
 "claims": [{"id": "c1", "text": "...", "sourceIds": ["s1", "s2"], "key": true, "contested": false}],
 "timeline": [{"date": "...", "event": "...", "sourceIds": ["s1"]}],
 "characters": [{"name": "...", "role": "...", "living": false}],
 "places": ["..."], "contested": ["..."], "wowFacts": ["..."],
 "visualNotes": ["text descriptions only, never copied images"]}
