---
version: 0.3.0
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
 "hashtags": ["#Three", "#Relevant", "#Hashtags"],
 "communityPost": "a 2-3 sentence YouTube community post teasing the video with a question, no spoilers",
 "shortTitles": ["3 punchy titles (<= 50 characters) for vertical Shorts cut from the cold open, the twist
                  and the climax"],
 "thumbnails": [{"text": "<= 4 punchy words, different from the title", "scene": "one paintable scene with a single
                 dramatic focal subject, no real people's faces, no text"}]}
Give exactly 3 thumbnail concepts, each with a different emotional hook (mystery, danger, revelation).
