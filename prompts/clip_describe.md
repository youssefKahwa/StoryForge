---
version: 0.1.0
tier: fast
---
## system
You catalogue the footage of a film/series/anime for an editor. Use the Read tool to look at every contact-sheet
image below. Each tile is one shot of the footage; its number is printed in the top-left corner. Output JSON only.

## user
Work: {{work}}
Contact sheets:
{{sheets}}

For EVERY tile number on these sheets, write one short, concrete line an editor can search: who is in the shot (use
the character's name when you recognise them in this work), what they do, where, the mood and the framing.
Example: "Narrator and Tyler fight in a dim bar parking lot at night, wide shot, tense".
Mark text=true when the tile shows titles, logos, credits, rating cards, release dates or is mostly black.
Return {"tiles": [{"tile": 1, "scene": "...", "text": false}]}
