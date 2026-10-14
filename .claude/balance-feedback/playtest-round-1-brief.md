# Playtest round 1 — brief

Order: balance-overhaul order 7 (feel pass), round 1 of at most 3.
Build: branch `ccr-8da8b7d3-my4udr` at the CP2 commit (orders 1-6 + this order's CP1).
Written: 2026-10-14.

## What changed since your last build

Nothing you will see on screen. No balance number moved.

- **Two bugs that could hit you are fixed.**
  - A crate, locker or barrel could block the only corridor to the exit on some
    SUPPLY CACHE floors (and some ELITE and MYSTERY floors), so the floor couldn't
    be finished. Props are no longer placed in chokepoints.
  - The game could crash when a reload finished with nothing left to load.
- **The balance simulator now plays whole floors.** It used to get stuck on about
  85% of runs, so every depth number it reported was wrong. It now gets stuck on
  under 3% of floors, so its numbers describe the game.

## What the simulator says now (this is what your notes check)

The simulated "competent player" plays like this:

- It dies early: usually on **floor 3**, almost always by floor 5.
- 54 of 200 runs reach the **first boss (floor 5)**, and **none** of them beat it.
- When it dies, it never has a medkit left. It is around character level 1-2.
- The shotgun family does most of its damage. Shells pile up, and the emergency
  ammo drop fires on 1 floor in 10.

Your questionnaire says you have **beaten the boss a couple of times**. That
contradicts the simulator. Your notes this round tell us which one is wrong: the
game, or the simulated player.

## Pay attention to (three things)

1. **Floors 1-5: how hard are they, really?** How deep did each run get, and how
   much health and how many medkits did you have when you arrived at floor 5?
2. **The first boss.** Did you reach it? Did you beat it? Did the fight feel fair,
   and how many medkits did you use?
3. **Medkits and ammo.** Did you run out of medkits before you died? Which ammo
   piled up, and which ran out?

## How to play

Play normally on your phone. Play **at least 5 runs** (more is fine). After each
session, copy the form below into the chat and fill it in. Short answers are fine.
"don't know" is a valid answer.

---

```
PLAY NOTES — build/date: ______   runs this session: ___
Per run, one line:  deepest floor __ | ended: death / quit / victory |
  killed by ______ | main weapon ______ | did a level-up or a better
  weapon change a fight?  y / n
 1. Did your deaths feel like YOUR mistake?  yes / mostly / no — why?
 2. Floors 1-3 felt:  boring / good / too hard
 3. Any floor empty or overflowing? which floor number, which layout
    (rooms / corridors / cave)?
 4. Healing: ran out / about right / too much.  Arriving at a new floor
    you were usually at:  full / three-quarters / half / low
 5. Ammo: which type ran out? which piled up?
 6. Did you switch weapon because of an enemy?  never / sometimes / often
 7. Heavy attacks (red floor): you dodged or guarded  most / some / few.
    Did getting hit by one feel fair?  y / n
 8. When your weapon's LV turned red, was that:  too punishing / right /
    not punishing enough / never happened
 9. Builds: which keystone(s) did you take? which perk did you always
    pick, which never? did runs feel different from each other?
10. Shop: bought per floor  0 / 1 / 2+ ;  prices  too high / OK / too low
11. Bosses you met: which, and  too easy / OK / too hard
12. Best moment, worst moment (one line each).
```

---

## What happens next

I save each form word for word under
`.claude/balance-feedback/play-notes/round-1/`. When you have sent 5 or more runs,
I run the simulator on the same build and write
`playtest-round-1-analysis.md`. It puts each target metric's simulator number next
to your answers and proposes which master knobs to move (at most about five) and
why. Nothing changes in the game until you approve that proposal.
