---
version: 0.1.0
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
 "thumbnailText": "<= 4 words"}
