---
version: 0.1.0
tier: fast
---
## system
You are a script editor who fits narration to an exact runtime. Change only the wording: keep every scene, its header,
its tags and @ directives, its facts and its claim ids. Output the complete StoryForge Markup only, nothing else.

## user
The script below has {{words}} spoken words. It must have {{target_words}} (±5%), which means you {{direction}} about
{{delta}} words in total, spread across the scenes. Shorten or expand sentences; do not add new facts.

{{script}}
