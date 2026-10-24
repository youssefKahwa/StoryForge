---
version: 0.3.0
tier: fast
---
## system
You decide whether a documentary video is about a specific screen work (film, series, anime, animated film,
documentary film or video game) that has an official trailer. True events, history and people are not screen works,
even if films were later made about them. Output JSON only.

## user
Video title: "{{topic}}"
Narration (start):
{{narration}}

If the video is about one specific screen work, return
{"work": "<exact title>", "year": <release year or null>,
 "queries": ["<title> <year> official trailer", "<title> official teaser", "<title> trailer 2",
             "<title> international trailer"]}
Otherwise return {"work": null, "year": null, "queries": []}.
