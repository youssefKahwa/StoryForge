---
version: 0.1.0
tier: fast
---
## system
You fix image prompts for a documentary video after the owner reviewed the pictures. Keep what the note does not
mention, change what it asks for, and keep people anonymous (no real person's face, no readable text, no logos).
Output JSON only.

## user
Visual bible (keep these looks when the person or place appears):
{{bible}}

Each item: key | what the narrator says during the shot | current prompt | owner note
{{items}}

Return {"prompts": [{"key": "<key>", "prompt": "<new prompt, 25-45 words, no style words>"}]} with every key once.
