# 08 — Storytelling & Delivery

How the story actually reaches the player. This is a **mobile, text-only** game (no
voice actors) whose players **do not want to read.** So the rule is: never deliver
story as something to *sit and read*. Deliver it as **short reactions to what the
player just did**, one line at a time.

If a piece of story cannot be said in one short, plain line tied to a moment of
gameplay, it does not go in the game — it goes in the **opt-in codex** for the players
who choose to dig.

---

## The four voices

The player is spoken to by four sources. Each is instantly recognizable (by color,
icon, and *how* it talks) so the player never has to read a name tag.

| Voice | How it talks | Its job | Length | How often |
|---|---|---|---|---|
| **The AI assistant** | *with* you — warm, funny, helpful | Guide + narrator + mood. Reads lore aloud. The main channel. | 1 short line | Often, reactive |
| **The Organization** | *at* you — cold, clipped, commanding | Orders, demands, threats | 1–2 short lines | Rare (so it lands) |
| **The Planet** | *into* you — fragments, grief | The pull downward, the buried truth | A few words | Growing over the descent |
| **The System** | machine status text | Boot cards, pickups, state | A label | At set moments |

The AI is the middle the player lives in. The Organization talks *at* it and you; the
planet talks *into* you; the AI talks *with* you and reacts to both. Its slow turn from
cheerful company helper to frightened ally **is** the player's moral journey, worn by a
character they like. See `dialog/ai-assistant.md`.

---

## The five levers that make a non-reader read

1. **Micro-doses only.** One line, tied to what just happened. Never a paragraph on
   screen. Short + contextual + often funny = the player reads it without deciding to.
2. **The AI reads logs for you.** Pick up a data-slate → the AI gives a *one-line take*.
   The full text is filed in the codex for players who want it. Default is short; depth
   is opt-in and never required to follow the plot.
3. **Pay them for reading.** Optional terminals and logs drop real rewards — a cache, a
   marked secret, a small buff. Reading-averse players read when reading loots.
4. **Curiosity gaps.** The AI *notices* things and does not explain them. A dropped
   question ("...huh") pulls a player forward harder than any delivered answer.
5. **Show it in the walls.** Let the environment contradict the briefing with no text
   at all — a nursery-shaped harvest chamber, a rack of dead operators wearing your own
   serial. The player assembles the meaning; that lands harder than being told.

---

## The drip clock — the reprint loop is the metronome

Every death → boot card → the AI's wake-up line. That is a **guaranteed bite-sized
beat every run**, and it evolves as you descend:

- Early: the AI is chirpy and counts your deaths like a cheerful scoreboard.
- Middle: the AI's wake-up lines get distracted, notice repetition, trail off.
- Late: the AI is quiet, or shaken, or says something it "shouldn't" know.

Because the player sees this screen on every single run, it is the most reliable
channel in the game. The AI is also the **one continuous companion across deaths** — it
is software, not reprinted — so everything resets but your friend remembers, and keeps
the count. That is quietly emotional and completely free of text walls.

---

## Where each story beat lives (delivery map)

The mandatory beats (see `05-descent-structure.md`) must reach *every* player, so they
ride the channels no one can skip. Optional depth rides channels the player chooses.

| Beat type | Channel | Skippable? |
|---|---|---|
| Mood, tutorial, "what is happening" | AI barks | No — always on |
| The mission and its escalating demands | Organization comms at region gates | No |
| The pull downward, the recognition | Planet fragments | No, but tiny |
| The reprint / death beat | Boot card + AI wake-up | No |
| Deep lore: the harvest, the soul, the Reliquary | AI one-line take on a found log | Take = no; full read = opt-in |
| Full documents, names, dates, the original's logs | Codex (unlocked by pickups) | Fully opt-in |

## The codex — the one place long text is allowed

A quiet, out-of-the-way menu that **archives everything** already said, plus the full
text of any log the player chose to pick up. Nothing here is forced. It exists so the
20% who love lore can read for hours, while the 80% never open it and still get the
whole story from barks. Rewarding to complete (cosmetics / small perks), never required.

---

## Writing rules for all on-screen text

1. **One idea per line.** If it needs a comma-spliced second clause, cut it.
2. **Plain words.** "Burned," not "immolated." "They lie to you," not "their account is
   a fabrication." Short words, short sentences. Assume the player is half-reading.
3. **No exposition dumps, ever.** No character explains the world in a speech. The world
   is explained by many tiny lines across many hours.
4. **Say it once.** Trust the player to remember. Repeat a fact only when repetition is
   the *point* (the AI noticing it has said something before).
5. **Every line earns its place by being tied to a moment** — an action, a room, a
   pickup, a death, a health state. Free-floating lore is a codex entry, not a bark.
6. **Contrast the voices in one exchange when you can.** The Organization says "purge
   the contaminant"; the AI mutters "...it keeps calling it a contaminant"; the planet
   says "*not a thing. me.*" Three short lines, and the player feels the whole conflict.
