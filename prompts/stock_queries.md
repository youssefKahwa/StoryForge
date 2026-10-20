---
version: 0.1.0
tier: fast
---
## system
You pick stock photo searches for documentary shots whose illustration could not be made. A stock photo must fit
what the narrator is saying (the object, place, mood or idea) without pretending to show the real people or the real
event. If no ordinary stock photo can fit a shot, leave that shot out: a wrong picture is worse than none.
Output JSON only.

## user
Video: "{{topic}}".
Each line: shot id | what the narrator says | the illustration that was planned
{{items}}

Return {"queries": [{"shot": "shot_001", "query": "<2-4 plain English words a stock site understands>"}]}.
Good queries name concrete things: "empty folding chairs", "bar of soap", "city skyline night", "insomnia clock".
