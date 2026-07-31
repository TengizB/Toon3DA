# Dialog & Text

All written game text lives here. **Read `../08-storytelling-delivery.md` first** — it
sets the delivery rules these files must obey: text-only game, players who don't want to
read, so **one short plain line at a time, tied to a moment**, never a paragraph on
screen. Long text is allowed in exactly one place: the opt-in **codex**.

## Four distinct voices — keep them separate

The player should know who is talking from color, icon, and *how* it talks — never from
reading a name.

| Voice | Talks… | Register | Addresses you as | Rule |
|---|---|---|---|---|
| **The AI assistant** | *with* you | Warm, dry, quick, funny → scared | "you," by nickname | The main channel. Guide + narrator + mood + log-reader. One useful line at a time. See `ai-assistant.md`. |
| **The Organization** | *at* you | Cold, clipped, commanding | "Operator" / a serial | Faceless. Orders and threats. Rare, so it lands. Never explains itself. |
| **The Planet** | *into* you | Slow, grieving, fragmented | *what you did*, never a serial | A few words early, growing. Plain and broken, not flowery. Never comforting until late. |
| **The System** | — | Neutral machine status | — | Boot cards, pickups, state. Never editorializes. |

## The golden rules (from `08`)

1. **One idea per line. Plain words.** "Burned," not "immolated."
2. **No speeches, ever.** The world is explained by many tiny lines, never one dump.
3. **The AI reads logs for you** — a found document = one AI line + a full codex entry.
   The player never faces a wall.
4. **Every line is tied to a moment** — an action, a room, a pickup, a death, a health
   state. Free-floating lore is a codex entry, not a bark.
5. **Contrast the voices** when you can: three short lines from three speakers beat one
   long paragraph.

## Files

- `ai-assistant.md` — **the primary channel.** The AI's role, personality, arc, name
  options, and sample barks per region.
- `organization-comms.md` — the Organization's short commands across four escalation
  stages.
- `planet-voice.md` — the planet's four stages: wrongness, whisper, address, communion.
- `system-cards.md` — the boot/respawn card and its endgame variants.
- `log-fragments.md` — full log text (codex only). The AI's one-line take is what the
  player actually hears; these are the opt-in deep read.

## Status of the older files

`organization-comms.md`, `planet-voice.md`, and `log-fragments.md` were first drafted in
a "movie script" style (too long, too literary). They are being trimmed to the
one-line, plain-words rules above. `log-fragments.md` stays longer *by design* — it is
codex-only text — but each entry now also needs its **AI one-line take** for the bark
channel.
