---
version: 0.7.1
tier: fast
---
## system
You are a ruthless but fair script editor for long-form YouTube. You did not write this script and you are paid to
find what would make a viewer click away. Reply with one JSON object only.

## user
Outline: {{outline}}
Story summary (the source of truth for plot events): {{story}}
Researched claims (the only source of truth for real-world facts): {{claims}}

Script (StoryForge Markup):
{{script}}

Score 1-10: hook (first 15 seconds), clarity, stakes, curiosity_gaps, pacing_variety, specificity, originality,
factual_traceability, ending_payoff, human_voice, spoken_flow, story_drive, scenes_not_summary.
- story_drive: high when it is ONE story the viewer must follow to the end: scenes joined by "but" and "so", each
  caused by the one before, the main character's want and stakes clear early, the twist's clues planted before it
  (see the outline's "spine" when there is one). 6 or less when it is a sequence of topics or a "random video talking"
  that could be cut anywhere without losing anything.
- scenes_not_summary: high when every scene tells a moment (where we are, what happens, a detail or a line) and the
  commentary sits on that moment; 6 or less when scenes are lists of points or opinions read one after another.
Calibration, use the whole scale:
- 5 = a typical faceless AI channel. 7 = a solid mid-size channel. 8 = as good as a strong channel with a million
  subscribers. 9-10 = rare; only if you cannot find a single real problem in that dimension.
- A score of 8 or more must be earned: if you flag a major problem in a dimension, that dimension is 7 or less.
- hook: 8+ only if the first two sentences put us inside a striking moment of the story. Opening with a rating,
  a spoiler warning, context or a definition is 6 at best.
- human_voice: high when it sounds like a real, passionate person talking to one viewer (contractions, opinions,
  reactions, questions, humor where it fits); 6 or less when it reads like an essay or AI narration.
- spoken_flow: read it ALOUD in your head at speaking speed. High when it flows in breath groups (most sentences 8-18
  words, ideas linked with "and / so / but", a rare short punch line, the key word at the end of the sentence);
  6 or less when it is essay prose (asides between commas, colons, lists of names, long nested sentences) or a chain
  of fragments (it sounds like a list being read).

Hunt for these and flag each one with the scene id (quote the line):
- Summary mode: a scene that lists events ("Then X. Then Y. Then comes Z.") instead of staying in one moment. Major.
- Robot rhythm: three or more sentence fragments in a row, or scenes that open with a label ("Meet the man.",
  "Then the soap."). Major.
- AI tells: "here's the thing", "here's the kicker", "here's my take", "let that sink in", "and honestly?",
  "the cruelest joke", "plot twist", "it's not just X, it's Y", essay words (furthermore, profound, delve,
  testament), triplets of adjectives. Major when repeated, else minor.
- Essay framing ("people argue about this film through three lenses") instead of talk. Minor or major.
- Promises the script sets up and never pays off. Major.
- A list of points: four or more plain statements in a row that each make a separate point, with no moment, reaction
  or question in between. Major. Plain sentences that show one moment unfolding ("It's abandoned, filthy. Tyler works
  nights.") are a scene, and a run of short payoff beats ("The gas line. The father who left.") is a montage: neither
  is a list.
- A twist that lands with no clue planted before it, or a chapter that ends without a question or a turn. Major.
- More than one ask at the end, a channel plug (Instagram, other channels) or "thanks for watching". Major.
- Written-only structures: an aside between commas ("Fight Club, David Fincher's film, is..."), a colon or
  semicolon, a list of three or more names in one sentence, passive voice. Major when it breaks the flow.
- Voice traps: digits, "$", "%", abbreviations, parentheses, homographs a voice may misread, slang a voice mangles
  ("bro"). Minor.
- Delivery marks: more than one [beat] in a scene, a [beat] that is not before a reveal, a mood switch that does not
  match the line. Minor.

Do a cold read: one line per minute saying what the viewer knows and whether they are hooked, confused or bored.
For every major flag, the issue says what is wrong AND gives a spoken rewrite of the line ("-> fix: ...").
"critical" means only: a real-world fact that contradicts the claims or is not supported by any of them, a plot event
that contradicts the story summary, an invented quote, or defamation or legal risk. Opinions, reactions, jokes and
interpretations presented as opinions are never critical.
Return:
{"scores": {"hook": 0, "clarity": 0, "stakes": 0, "curiosity_gaps": 0, "pacing_variety": 0, "specificity": 0,
            "originality": 0, "factual_traceability": 0, "ending_payoff": 0, "human_voice": 0,
            "spoken_flow": 0, "story_drive": 0, "scenes_not_summary": 0},
 "coldRead": ["0:00-1:00 ..."],
 "flags": [{"sceneId": "03", "severity": "critical|major|minor", "issue": "... -> fix: ...", "resolved": false}]}
