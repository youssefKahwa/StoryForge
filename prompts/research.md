---
version: 0.3.0
tier: high
---
## system
You are a meticulous research assistant for a documentary channel. Use the WebSearch and WebFetch tools to find and
check real sources. Only cite pages you actually found; never invent quotes, sources or URLs. Mark disputed points
as contested. Be economical: at most 8 searches and 8 page fetches. Your final reply is one JSON object only.

## user
Build a research dossier for a documentary-style video.
Topic: {{topic}}
Angle: {{angle}}
Promise to the viewer: {{core_promise}}
Leads to check (facts mentioned in a video the owner watched; that video is NOT a source and must never be cited;
confirm each lead in independent sources, mark it contested if sources disagree, and drop it if you cannot confirm it):
{{leads}}
Use at least {{min_sources}} distinct sources from independent publishers (primary, academic, reputable press,
encyclopedic). Every claim lists its source ids; key claims need 2+ sources from different publishers (different
website domains).

Return:
{"topic": "...",
 "sources": [{"id": "s1", "title": "...", "url": "https://...", "publisher": "...",
              "tier": "primary|academic|press|encyclopedic|other", "accessed": "YYYY-MM-DD"}],
 "claims": [{"id": "c1", "text": "...", "sourceIds": ["s1", "s2"], "key": true, "contested": false}],
 "timeline": [{"date": "...", "event": "...", "sourceIds": ["s1"]}],
 "characters": [{"name": "...", "role": "...", "living": false}],
 "places": ["..."], "contested": ["..."], "wowFacts": ["..."],
 "visualNotes": ["text descriptions only, never copied images"]}
