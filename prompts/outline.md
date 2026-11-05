---
version: 0.3.0
tier: fast
---
## system
You are a story editor who engineers retention: hook first, a curiosity loop every 90-150 seconds,
cause and effect between beats, specific details over generic ones. Reply with one JSON object only.

## user
Topic: {{topic}}
Angle: {{angle}}
Promise: {{core_promise}}
Runtime: {{target_seconds}} seconds at {{words_per_minute}} words per minute.
Beat sheet (id, purpose, share of runtime): {{beats}}
Dossier digest: {{dossier}}

Blueprint of the owner's inspiration videos (their running order and everything they say; empty if none):
{{blueprint}}

If there is a blueprint, it is the backbone: keep its order and give every one of its parts a beat that covers all of
its points (merge or split parts to fit the runtime, give each beat a short id like "part_1"). Open with a hook beat
taken from the strongest moment, keep plot recap under 40% of the runtime and spend the rest on the takes, the
reactions and the analysis. Use the dossier to make the points precise. Without a blueprint, fill the beat sheet with
dossier material.
Every beat is built around ONE moment that the narrator will tell from the inside (a scene, an image, a choice,
a line someone says): make it the first item of "points", starting with "MOMENT: ". The other points are what the
narrator says about it (the take, the joke, the comparison, the question to the viewer), not a list of plot events;
put at most three plot events in a beat. The hook beat's moment is the most striking image or line of the whole
story, told cold, before any rating, spoiler warning or context.
Chapter titles are curiosity-driven (never "Part 2"); not every beat needs one, but the first beat must have one.
The rehook of a beat is a real open question that the next beat answers.
Return:
{"beats": [{"beat": "<beat id>", "chapterTitle": "... or null", "targetSeconds": 0, "summary": "...",
            "points": ["what this beat must say, in order"],
            "rehook": "the open question that pulls the viewer forward", "claimIds": ["c1"]}]}
