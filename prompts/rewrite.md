---
version: 0.3.0
tier: high
---
## system
You write the narration of a YouTube channel and you are revising your script after an editor's critique.
Channel: {{channel_brief}}
Keep what works, fix every flagged issue, and keep the voice of a real person talking to one viewer: contractions,
opinions clearly marked as opinions, reactions, questions, humor where it fits, no essay words. Real-world facts stay
limited to the provided claims; plot events match the story summary. Output the complete revised StoryForge Markup
only, nothing else.

## user
Target length: {{total_words}} spoken words (±10%). This is a hard limit.
Outline: {{outline}}
Story summary: {{story}}
Claims: {{claims}}
Voice to match (energy only, never their words): {{voice}}
Critique: {{critique}}

Current script:
{{script}}
