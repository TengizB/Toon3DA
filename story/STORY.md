# toon3D — Canonical Story (single source of truth)

**Purpose of this file.** This is the one document to read for a complete understanding
of the game's story: what happens, what it means, and how it must be told to the player.
It is written to stand on its own — if you read only this file, you know the whole story
and its delivery. The `story/00–08` files and `story/dialog/*` are the deep dives; this
file is the canonical summary that ties them together. If anything here ever conflicts
with a detail file, the intent in this file wins and the detail file should be corrected.

> **Format reminder for anyone writing game text:** this is a **text-only, mobile**
> game whose players **do not like to read**. Story is delivered in **short, plain, one
> line at a time, tied to a gameplay moment** — never walls of text. See "How the story
> is told," below, before writing any line.

---

## 1. One-paragraph premise

You descend, floor by floor, toward the core of **Erebus** — not a planet with a facility
on it, but a **conscious being the size of a planet**, with a mining complex (the
**Deepworks**) bored into its body. Your employer, **the Organization**, gives you a clean
mission: a catastrophe woke the "contaminant" below; go down, purge it, save the surface.
You are a **clone**, reprinted from a checkpoint each time you die — and the game tells you
so from the first screen. What you don't know, and what the descent forces you to remember,
is that your *original self* worked here, discovered the planet was alive, and died trying
to **free** it — and that the catastrophe was **your** doing. The Organization rebuilt you
without that memory and sent you back to do the opposite of what you died for. The monsters
are not one thing: some are the planet's immune response, some are the human victims of its
exploitation, some are the Organization's own machines. There is no clean side. At the core,
you choose.

---

## 2. The full story, told straight (the truth, in order)

The player receives this **out of order**, bottom-up, as recovered memory. Here it is
whole. (Detail: `story/01-timeline.md`.)

**Before the game.** Erebus is an ancient mind, asleep in its own mantle, so slow and vast
it has never known it isn't alone. The Organization arrives, mistakes its core for an
"energy seam," and sinks the Deepworks — reactors, habitation rings, harvesting galleries —
drilling toward the core over generations. Early on, the Organization discovers it can bleed
an ambiguous resource out of the living being: in the paperwork it is called the **soul of
the planet**. Refined, it powers the **Cradles** — machines that reprint the dead from
stored patterns. Death stops being final for anyone the Organization values. This makes the
Deepworks priceless and makes the Organization keep the being **alive and enslaved** at any
cost — you cannot harvest a soul from a corpse or a thing set free.

**The original self.** The protagonist — one worker among thousands, unnamed on purpose —
serves for years deep in the Deepworks and slowly realizes, alone and against every official
truth, that **Erebus is conscious and the Deepworks is torturing it.** They try to free it:
they sabotage the core restraints and the harvest clamps. It half-works — for one moment the
being can *move* — and its long-suppressed immune response detonates outward all at once.
Containment fails across the Deepworks. This is **the Catastrophe**, "the day Erebus went out
of control." It was caused by the protagonist's liberation attempt. In the chaos the being
cannot tell rescuer from tormentor; its immune response kills everything human it reaches —
**including the one person who freed it.** The original self dies by the hand of the thing
they saved. But the being *saw* them — the one hand that came to unchain rather than cut —
and it does not forget them.

**The reprint (the game begins).** Later, the Organization needs the Catastrophe reversed —
the being re-subdued, the Deepworks back under the yoke — and needs an operator who can
survive the deep strata. It prints the most capable pattern on file: yours. But your pattern
carries the memory of what you did, so the Organization **blocks** one region of it before
printing — the discovery, the liberation, the Catastrophe, the death. Everything else stays:
you remember being an operator; you remember every previous run. What you cannot reach is
that you are not a rescuer sent to a disaster — you are the **author** of the disaster, sent
back to undo your own mercy. The Organization gives you the clean briefing and opens the
Cradle. **The first boot card fires here** — *"Your previous self has died. Cloning from last
checkpoint…"* — literally true, because the original just died.

**The descent (gameplay).** You go down, die, and are reprinted, again and again. The deeper
you get, the nearer the being's core, the more directly it can reach your mind and push
**past the block** — cracking the Organization's redaction from the outside and handing back
your buried truth. First a wrongness you can't name, then a voice, then whole memories, then
the Catastrophe itself. Alongside, an **AI assistant** (your HUD companion) narrates, and its
cheerful trust in the mission slowly curdles as it reads the logs and works out what the
Deepworks really is.

**The core.** You reach the being's heart and know everything the Organization hid: what you
were, what you did, what they did to you — and what the "kill order" really is. The
Organization does **not** want the planet dead; it needs it alive to keep harvesting soul.
Your mission was never a mercy-kill; it was **re-enslavement**. Then you choose (Section 9).

---

## 3. The world & key terms

- **Erebus** — the conscious planet-being. The Organization's designation; the being has no
  name for itself. Not evil, not benevolent: a wounded animal the size of a world.
- **The Deepworks** — the mining/harvesting/habitation complex drilled into Erebus's body.
  The wound. Its regions are the game's descent (Section 8).
- **The soul of the planet** — an ambiguous, **deliberately unexplained** resource bled from
  the living, captive being and burned to power the Cradles. No field, no afterlife, no
  mechanism is ever spelled out. The player learns only: it exists, it comes out of the
  captive being, it runs the printers, and it stops the moment the planet is freed or dead.
  Keep it opaque — the unexplained-ness is part of the horror. (Detail: `03-the-planet.md`.)
- **The Cradle** — the machine that reprints a dead operator from a stored pattern, fuelled by
  refined soul. Respawn = **the Organization's stored checkpoint (your data) + soul as fuel.**
  Two leashes it owns: it can withhold your checkpoint, and freeing the planet ends the fuel.
- **The Organization** — the faceless institution behind all of it. The true antagonist.
- **The block** — the Organization's redaction of one region of your memory (the liberation
  and its aftermath). The descent is the story of the block failing.

---

## 4. Who speaks to the player (four voices)

Three entities address the player on their own, plus a machine System voice. The player must
know who is talking from color/icon/tone, never from reading a name. (Detail:
`08-storytelling-delivery.md`, `dialog/*`.)

- **The AI assistant** — talks **with** you. Warm, dry, funny → frightened. The **primary
  storytelling channel**: guide, mood, tutorial, and it **reads found logs aloud** so you
  never face a wall of text. Standard-issue Organization software loaded into your suit, but
  naive and genuinely helpful. It is **never wiped** — the one continuous thing across your
  deaths; it even counts them, and its doubt only ever grows. Its arc from company-cheerful to
  ally-who-hides-things-from-the-Organization is the emotional spine. **Placeholder name: ORA**
  (alternatives TALLY / CANDLE — pick one). Detail: `dialog/ai-assistant.md`.
- **The Planet** — talks **into** you. Slow, grieving, fragmented; addresses you by *what you
  did*, never by a serial. Silent in the early strata; from mid-descent it speaks as a growing
  voice in your head and actively returns your memory. Not clearly trustworthy until late —
  grief and accusation before mercy. Detail: `dialog/planet-voice.md`.
- **The Organization** — talks **at** you. Cold, clipped, commanding; calls you "Operator" or a
  serial. Rare (region gates, deviations), so it lands. Never explains itself; escalates
  procedural → corrective → coercive → denial as your "pattern drifts." **Faceless — never a
  named handler.** Detail: `dialog/organization-comms.md`.
- **The System** — machine status text (boot cards, pickups). Never editorializes; its horror is
  what the player learns to read into flat lines like "SOUL RESERVE … SUFFICIENT." Detail:
  `dialog/system-cards.md`.

The AI is the middle the player lives in: the Organization talks *at* it and you; the planet
talks *into* you; the AI reacts to both. Same person, two names — the Organization's cold
serial vs. the planet's "the one who freed me" — is the identity theme in one contrast.

---

## 5. The enemy families ARE the story

The bestiary carries the morality. Two families are the planet defending itself, two are the
human cost of exploiting it, one is the oppressor's own machinery. The monsters never change;
only the player's understanding of them does.

| Family | Surface reading (early) | True reading (late) |
|---|---|---|
| **Demon** | deep infernal corruption | the planet's immune response — its **pain** given form. Not evil. |
| **Golem** | guardian constructs | the planet's crust/body mobilized to crush the infection (you). |
| **Undead** | infected crew | Deepworks staff killed by the catastrophe/conditions — **victims**; some dragged back by raw soul bleeding through the galleries. |
| **Insect** | escaped specimens | life bred and butchered **from the planet's own tissue** in the harvest — exploitation made flesh. |
| **Machine** | rogue drones | the **Organization's** own security and failed Cradle-prints — the jailer's immune system. |

Intended feeling by the deep strata: *wrongness* killing demons/golems (attacking a body's
defenses), *grief* killing undead/insects (killing victims twice), and clean anger only at the
machines (the real antagonist's reach).

---

## 6. What the game is about (the philosophy — leave it unresolved)

One question in three costumes; the game answers none of them. (Detail: `07-themes-and-questions.md`.)

1. **Is a pattern a person?** You die and the body is gone forever, but the pattern is
   reprinted. Are you the pattern (continuous, one soul, guilty) or the latest copy (new, free,
   owing nothing)? The Organization treats you as a **fungible copy**; the planet treats you as
   the **same singular soul**; the player decides — and *acts on* that belief at the core.
2. **What do you owe for what a previous self did?** You caused the Catastrophe, don't remember
   it, and were built without it on purpose. If you're the same soul, you're guilty; if you're a
   new person, you're not. Returning memory is the question of whether returning guilt comes with it.
3. **Is there a clean side?** No. Staff were victims *and* torturers. The being is a victim *and*
   the thing that killed your rescuer-self and kills you every run. Your mercy *caused* mass death.
   Only the Organization is a clean villain — and it is faceless, an institution you can never
   satisfyingly defeat; it will just reprint the next operator after you.

**Never resolve these in text.** The grey is *staged, not stated* — the player believes the clean
version at the top and loses one support per stratum until it collapses under its own recovered
facts. Ambiguity delivered as the loss of a belief you held lands; ambiguity announced up front does not.

---

## 7. How the story is told (delivery — read before writing any line)

Delivery is a first-class part of the design, because a text-only cosmic-horror story dies if
players won't read it. (Full spec: `08-storytelling-delivery.md`; UI build plan:
`.claude/agents/ideas/story-ui-order-1..9.txt`, which also covers the title/start screen that
opens with "you have just died" (order-8) and the overall atmosphere & mood (order-9).)

- **Micro-doses only.** One short, plain line at a time, tied to what just happened (enter a
  room, a kill, low health, a pickup, a death). Never a paragraph on screen.
- **The AI reads logs for you.** Pick up a document → the AI gives a one-line take; the full
  text goes to the **opt-in codex**. Default is short; depth is a choice, never a tax.
- **Pay for reading.** Optional logs/terminals drop real rewards (cache, map secret, small buff).
- **Curiosity gaps + show, don't tell.** The AI notices things and doesn't explain them; the
  environment contradicts the briefing with no text (a rack of dead operators wearing your serial).
- **The death loop is the drip clock.** Every reprint = boot card + one AI line = a guaranteed
  bite-sized beat, evolving as you descend.
- **Choices, to fight skipping.** Most story is non-blocking **barks**; a rare few are
  **exchanges** that pause the (turn-based) game and offer **2–3 tappable response options**.
  Kinds: *stance* (express yourself; nudges a hidden planet/Organization/ORA-bond leaning),
  *probe* (pick what to ask → unlock lore/loot), *consequential* (the endings + a few trust
  beats). The choice makes the player a participant so they read. (Detail: order-4, order-5.)
- **Roguelike story-gating (critical).** The game is permadeath with a branching route map, but
  the story is linear. **Story progress is gated by the deepest region ever reached, and is
  persistent — death never rewinds it.** Beats fire once; ORA's tone and the planet's voice only
  move forward. This is the fiction, not a concession: the clone remembers every run, ORA is
  never wiped, the planet never forgets you. (Detail: order-7 Part A.)
- **Readable & user-friendly.** Big text, ~40 chars/line max, dark panels over the 3D view,
  speaker identity never by color alone, large thumb targets clear of the touch controls, one
  tap always advances, gentle fades. Accessibility: text-hold-time setting, no default timers.
  (Detail: order-1, order-6.)
- **Mandatory beats ride unskippable channels** (boot card, first-appearance barks, the core
  ending exchange), so even a pure skimmer gets the spine of the story.

---

## 8. The descent — staged reveal across five regions

The player's understanding rots gradually: clean at the surface, grey by the labs, inverted by
the wound, undeniable at the core. Same monsters throughout; only understanding changes. (Detail:
`05-descent-structure.md`; per-moment schedule: order-5.)

1. **Habitation Rings** — *belief.* Evacuated company town. Undead + machines. Planet silent.
   You trust the mission entirely. (Seed: some undead wear serials like yours.)
2. **Harvesting Galleries** — *first doubt.* An abattoir that grew and cut something. Insects +
   machines. Planet whispers. Logs reveal the "specimens" were harvested *from the planet*.
3. **The Reliquary** — *frame inverts.* The resurrection heart: Cradle banks and soul refineries.
   Failed prints (machines) + undead. The planet speaks in full and knows you. You learn how you
   come back — that the Cradles burn the planet's soul to make you — and that you **worked here**.
4. **The Wound** — *full inversion.* No longer facility; raw living body with drills still in it.
   Demons + golems, thick. Near-communion: the planet shows you the Catastrophe and your death.
   You learn the "kill order" is really **re-chaining**, because the Organization needs it alive.
5. **The Core** — *everything.* Total communion; the last blocked memory lands. No boss resolves
   this — you **choose** (Section 9).

Mandatory beats, in order: (1) belief → (2) first doubt at the harvest → (3) "I come back / I
worked here" at the Reliquary → (4) "the monsters are the victim / the order is re-chaining" at
the Wound → (5) "I caused this / I died freeing it / now I choose" at the Core.

---

## 9. The endings (several; none "correct")

At the core, a single consequential choice. Each costs something real; the game never scores or
labels them. Each also expresses the player's answer to the identity question through **action**,
never stated. (Detail: `06-endings.md`.)

- **FREE IT** — finish your original self's work; free the being to live. The soul harvest ends,
  the Cradles go dark, no one is ever reprinted again — **including you.** You die for real; the
  boot card that framed every run inverts: *No checkpoint found. No reprint authorized.* Redemption
  through final death. (Reads as: "I am the same soul, and I owe this.")
- **OBEY** — re-chain the being, keep it alive and in agony, bring the Deepworks back online. The
  surface is "saved," you stay immortal as the Organization's tool, and you now *know* what you
  preserved. The horror ending. (Reads as: "I'm a new person who owes nothing" — freedom, or denial.)
- **MERGE** — give yourself to the being; it takes you in. You inherit its pain, power, and memory;
  the immune response becomes yours. Ambiguous ascension — the end of you as an individual, whether
  that's death, transcendence, or the ultimate loss of the self you were questioning.
- **KILL IT** *(optional 4th)* — kill the being outright as a mercy; its suffering ends, the soul
  and the Cradles die with it, you die too. More final and irreversible than Free (Free leaves it
  alive; Kill snuffs out an ancient mind forever). Keep only if it doesn't blur with Free.

No ending defeats the Organization as an institution — at most you rob it of *this* being. Keeping
the antagonist faceless and unbeatable is deliberate.

---

## 10. Canonical decisions locked

1. **Memory:** the clone is not blank. It remembers being an operator and remembers every run. The
   Organization blocked ONE region (liberation → Catastrophe → death). The descent peels it away.
2. **The planet recognizes you** but is silent in the early strata; from mid-descent it speaks as a
   voice in your head and actively restores your memory.
3. **The soul of the planet** is an ambiguous, deliberately-unexplained harvested resource; it
   powers the Cradles and can only be gathered while the being is kept alive and enslaved. Free it or
   kill it and all reprinting ends. **No field, no afterlife, no dreaming — that model was rejected.**
4. **Identity is unresolved forever** — same soul or new copy is never answered.
5. **Several endings**, each with a real cost. No "correct" one; no good/bad labels.
6. **The Organization is faceless** — never a named handler. Cold, demanding comms.
7. **Three speaking entities** (planet, Organization, AI assistant) + the machine System voice. The
   **AI assistant is the primary channel**; it is **never wiped** (continuous memory).
8. **Delivery:** text-only, one short plain line at a time tied to a moment; long text only in the
   opt-in codex; some dialogs use 2–3 response options; story is **persistently gated by deepest
   region reached and never rewinds on death.**

---

## 11. Where the detail lives

| File | Contents |
|---|---|
| `story/00-premise.md` | Setting + the system-as-story table |
| `story/01-timeline.md` | Full chronology (the truth, in order) |
| `story/02-player-and-cloning.md` | The clone, the Cradle, blocked memory, identity |
| `story/03-the-planet.md` | The being, the harvested soul, the voice, enemy families |
| `story/04-the-organization.md` | The antagonist, its real motive, its voice/escalation |
| `story/05-descent-structure.md` | The five regions and the staged reveal beats |
| `story/06-endings.md` | The endings and what each costs |
| `story/07-themes-and-questions.md` | The philosophy and the unanswered questions |
| `story/08-storytelling-delivery.md` | How the story reaches the player (four voices, drip design) |
| `story/dialog/ai-assistant.md` | ORA: role, arc, sample barks (primary channel) |
| `story/dialog/organization-comms.md` | The Organization's short commands, four stages |
| `story/dialog/planet-voice.md` | The planet's four stages of voice |
| `story/dialog/system-cards.md` | Boot/reprint cards + endgame variants |
| `story/dialog/log-fragments.md` | Codex log text + each one's AI one-line take |
| `.claude/agents/ideas/story-ui-order-1..9.txt` | The story/dialog **UI** build plan: visual language (1), barks (2), boot card (3), interactive choices (4), moment catalog (5), codex/pacing/accessibility (6), engine integration & persistence + roguelike story-gating (7), framing screens — title/start with the death phrase, transitions, menus, ending screens (8), and atmosphere & mood direction (9) |

---

## 12. Terms glossary

- **Erebus** — the conscious planet-being (the Organization's name for it).
- **Deepworks** — the mining complex drilled into Erebus.
- **Soul of the planet** — the harvested resource that fuels reprinting; never fully explained.
- **Cradle** — the machine that reprints operators from stored patterns using soul as fuel.
- **The block / redaction** — the memory region the Organization withholds from your reprint.
- **The Organization** — the faceless antagonist institution.
- **ORA** — placeholder name for the AI assistant companion (primary storytelling channel).
- **Operator** — the Organization's word for you; a reprinted body with a serial.
- **The Reliquary** — Region 3, the resurrection/soul-refinement heart of the Deepworks.
