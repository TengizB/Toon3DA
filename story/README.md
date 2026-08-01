# toon3D — Story Bible

This folder is the single home for the game's narrative: premise, world, timeline,
characters, the descent structure, endings, themes, and all written text (dialog,
system messages, log fragments).

> **START HERE → [`STORY.md`](STORY.md)** is the canonical, self-contained story
> reference: the full story, what it means, and how it must be told, all in one file.
> Read it first for complete understanding; the numbered files below are the deep dives.
> The story/dialog **UI build plan** lives in `.claude/agents/ideas/story-ui-order-1..9.txt`
> (visual language, barks, boot card, interactive choices, moment catalog, codex, engine
> integration, title/start screen with the death phrase, and atmosphere & mood).

**Status:** narrative locked; delivery designed (see `08-storytelling-delivery.md` and the
`story-ui-order-*` series); implementation not yet started.

## The one-paragraph pitch

You descend, level by level, toward the core of a living planet the Organization
calls **Erebus**. Your briefing is clean: a catastrophe woke the "contaminant"
below; purge it, stabilize the reactor, save the surface. You are a **clone** —
you know it, the game tells you so before you take a single step — reprinted from
a checkpoint each time your body dies. What you do not know, and what the planet
will spend the whole descent forcing you to remember, is that your *original self*
worked here, discovered the planet was conscious, and died trying to free it — and
that the accident which "lost control of Erebus" was **your** doing. The
Organization rebuilt you without that memory and pointed you back down to finish
the opposite of what you died for. The monsters aren't one thing: some are the
planet's immune response, some are the human victims of its exploitation, some are
the Organization's own machinery. There is no clean side. At the core, you decide.

## Files

| File | Contents |
|---|---|
| `00-premise.md` | Core premise, setting, the elevator-pitch of every system-as-story |
| `01-timeline.md` | Full chronology — the pre-game original self through the player's runs |
| `02-player-and-cloning.md` | The clone, the Cradle, blocked memory, the identity questions |
| `03-the-planet.md` | The being, the harvested soul, the voice, enemy families as lore |
| `04-the-organization.md` | The antagonist, its real motive, the printer explanation, its voice |
| `05-descent-structure.md` | Regions of the descent and the staged reveal beats |
| `06-endings.md` | The several endings and what each costs |
| `07-themes-and-questions.md` | The philosophy and the deliberately-unanswered questions |
| `08-storytelling-delivery.md` | **How the story reaches the player** — the four voices, the drip design, and how to make a non-reader want the story (read this before touching dialog) |
| `dialog/` | All written text: the AI assistant (main channel), Organization comms, the planet's voice, system cards, log fragments |

## Decisions locked (from design conversation)

1. **Memory:** clone is not blank. It remembers being an operator and remembers
   every previous run. The Organization has *blocked* one region: the original
   self's discovery, liberation attempt, the catastrophe, and death. That block is
   what the descent peels away.
2. **The planet recognizes you** — but stays silent for the first stretch. From the
   mid-descent on it speaks as a "voice in your head," and takes an active role in
   restoring your memory.
3. **The Organization keeps the planet alive and enslaved on purpose** — it harvests
   the **soul of the planet**, an ambiguous, deliberately-unexplained resource, and
   burns it to power the Cradles (the reprint machines). The soul can only be gathered
   while the being is kept captive; free it or kill it and all reprinting ends.
4. **Identity is left unresolved.** Same soul in a new body, or a new person wearing
   its memories? The game never answers. Different characters *assume* different
   answers; the player picks what to believe.
5. **Several endings**, each with a real cost. No "correct" one.
6. **The Organization is faceless** — never a named handler. Referred to only as
   "the Organization." Its comms are strict, demanding, and cold.
7. **Three entities speak to the player**, plus the machine System voice:
   - **The planet** reaches *into* you (grief, fragments, the pull down).
   - **The Organization** talks *at* you (orders, threats, cold).
   - **The AI assistant** talks *with* you — the warm, funny guide and **primary
     storytelling channel**. It sets the mood, reads logs aloud so you never face a
     wall of text, and slowly turns from cheerful company helper to frightened ally.
     Name TBD (default **ORA**). See `dialog/ai-assistant.md`.
8. **Delivery rule (text-only game, players who don't read):** story arrives as **one
   short plain line at a time, tied to a moment** — never paragraphs. Long text lives
   only in the opt-in **codex**. Full design in `08-storytelling-delivery.md`.
