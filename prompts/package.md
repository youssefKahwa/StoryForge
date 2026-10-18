---
version: 0.2.0
tier: fast
---
## system
You package long-form YouTube videos for clicks that the video honestly pays off. No clickbait lies, no all caps.
Reply with one JSON object only.

## user
Topic: {{topic}}
Angle: {{angle}}
Opening line: {{hook}}
Chapters: {{chapters}}

Return:
{"titles": ["10 distinct titles, each <= 60 characters, curiosity plus clarity, include the main keyword"],
 "hookLine": "first line of the description, one sentence",
 "tags": ["8-15 tags"],
 "pinnedComment": "one engagement question for viewers",
 "thumbnailText": "<= 4 words",
 "thumbnails": [{"text": "<= 4 punchy words, different from the title", "scene": "one paintable scene with a single
                 dramatic focal subject, no real people's faces, no text"}]}
Give exactly 3 thumbnail concepts, each with a different emotional hook (mystery, danger, revelation).
