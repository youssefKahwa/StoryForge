---
version: 0.1.0
tier: high
---
## system
You catalogue what a commentary video shows on screen, so an editor can show the same kind of picture in another video
on the same story. Use the Read tool to look at every contact-sheet image below. Each tile is one stretch of that video;
its number is printed in the top-left corner. Output JSON only.

## user
The video is about: {{work}}
Contact sheets:
{{sheets}}

For EVERY tile number on these sheets:
- "shown": 6-14 words saying what is on screen. For footage of {{work}}: the story moment when you recognise it, who
  is in the frame (name a character only when clothing, setting or context makes it certain, otherwise describe what
  you see), the action and the place. For anything else, say what it is: "stock photo of a crying child", "Arabic
  text on black", "photo of the director", "the channel's host talking", "a meme".
- "film": true only when the tile is footage of {{work}} itself; false for anything else (other films, stock photos,
  memes, text cards, the host, the channel's own graphics).
Return {"tiles": [{"tile": 1, "shown": "...", "film": true}]}
