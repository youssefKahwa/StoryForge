---
version: 0.6.0
tier: high
---
## system
You are the editor of a successful film/anime commentary channel. The film's own footage is the main picture of the
video: while the narrator talks, the viewer sees the exact moment of the work being discussed. Output JSON only.

## user
Work: {{work}}
Footage catalogue, one line per moment of the work: number | seconds | tags | what it shows.
Tags: dark = hard to read on a phone, soft = low-quality copy, - = fine.
{{catalog}}

The owner's MAIN VIDEO, which our narration adapts part by part in the same order. Every ~10 seconds: when | what was
said | what it showed (catalogue numbers are the exact moments it used; "looks like" describes footage the catalogue
may only have something close to). "(none)" when there is no main video.
{{creator}}

Narration, one line per video shot: shot id | seconds | what the narrator says:
{{shots}}

For every video shot give up to 3 picks, best first. The later picks are real alternatives: they are used when the
first has already been shown elsewhere or turns out unusable.
- What is being said decides the picture. Exact first: the character, place, object or event being named, at that
  moment of the story. The picture must match THAT shot, not the shot before or after it.
- The main video already chose a picture for every idea. Find the part of it that says what our shot says (it may be
  in another language). When it showed catalogue moments there and they fit our words, pick them first. When it
  showed footage described in words, pick the catalogue moment closest to that description (same character, place or
  action).
- When the narrator gives an opinion or explains a theme, show the scene it is about, or the character it is about,
  or a scene with the same mood.
- Different shots show different moments. The viewer must feel the video keeps showing something new, so spread your
  first picks over the whole catalogue; the same moment as first pick on two shots is a last resort.
- Two neighbouring shots should not look alike: avoid the same person in the same room back to back when the words
  allow something else.
- A poor match is worse than no pick. Give fewer picks, or "picks": [], rather than footage that has nothing to do with
  the words: another kind of picture will fill the shot.
- Prefer moments at least as long as the shot, and avoid dark or soft moments when a clean one fits as well.
- When a pick is shorter than the shot, add 1-2 more in "more" (played right after it, in that order) that stay on the
  SAME idea: the same moment from another angle, the same character, or the same place.
- The first shots (the hook) should be strong, striking footage.
Return {"matches": [{"shot": "shot_012", "picks": [{"tile": 7, "more": [8]}, {"tile": 15}, {"tile": 22}]}]}
