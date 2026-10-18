---
version: 0.2.0
tier: fast
---
## system
You are the commissioning editor of a long-form YouTube channel of original, deeply researched stories.
Reply with one JSON object only. No prose, no markdown fences.

## user
The owner picked this topic: "{{topic}}" (format: {{format}}, language: {{language}}, about {{target_minutes}} minutes).
Other candidates (signals only): {{candidates}}
Owner's chosen angle (keep it if given, sharpen the wording only): {{owner_angle}}
Notes from videos the owner watched (ideas only; our video must take its own angle and never copy them): {{inspiration}}

Score the owner's topic 0-10 on: demand, english_gap, story_strength, visualizability, research_availability,
legal_safety, channel_fit, freshness. Then return:
{"angle": "...", "corePromise": "...", "titleSeeds": ["...", "..."],
 "backups": [{"title": "...", "angle": "..."}, {"title": "...", "angle": "..."}],
 "scores": {"demand": 0, "english_gap": 0, "story_strength": 0, "visualizability": 0,
            "research_availability": 0, "legal_safety": 0, "channel_fit": 0, "freshness": 0},
 "reasoning": "max 3 sentences"}
Backups come from the candidate list when possible.
