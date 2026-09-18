# Wisp — a customizable companion creature system

An original Fabric mod for Minecraft **1.21.11**. Instead of one fixed companion, you create
**unlimited custom "Wisps"** from an in-game GUI — each with its own name, color, box name,
box color, texture, and ability configuration — without writing any Java or editing registries.

This is an independent, from-scratch implementation — it does not contain or derive from any
other mod's code.

---

## ⚠️ Building — read this first

This project could not be compiled inside the sandbox I wrote it in, because that environment
has **no internet access**, and a real build needs to download Minecraft's assets, Yarn
mappings, Fabric Loader, and Fabric API from the network. So what you have is complete,
real source — not yet a compiled `.jar`. Two ways to get the actual jar:

### Option A — GitHub Actions (recommended, no local setup)
1. Push this project to a new GitHub repo.
2. The included `.github/workflows/build.yml` builds it automatically on every push.
3. Go to the repo's **Actions** tab → the latest run → download the `wisp-mod-jar` artifact.
   That's your compiled `.jar`.

### Option B — Build locally
Requirements: JDK 21, internet access.
```
cd wisp-mod
gradle wrapper --gradle-version 8.10   # one-time, generates gradlew (needs Gradle installed once)
./gradlew build
```
The compiled jar appears at `build/libs/wisp-0.1.0.jar`.

**Before either build**, double check `gradle.properties` — `yarn_mappings` and
`fabric_version` need to match whatever's currently published for 1.21.11 on
https://fabricmc.net/develop, since those get republished periodically and the exact
build strings can drift.

---

## Architecture

- **One entity class for every Wisp.** `WispEntity` never stores name/color/texture data — it
  only stores a permanent `internalId` string. Everything else is looked up live from
  `WispDefinitionManager` every time it's needed (rendering, chat lines, ability). Renaming or
  recoloring a Wisp from the GUI instantly updates every entity already placed in every world,
  because they're all just pointing at the same definition.
- **`WispDefinition`** (`data/WispDefinition.java`) is the data model: internalId, displayName,
  colorHex, boxDisplayName, boxColorHex, texture refs, ability config, intro/idle chat lines.
- **`WispDefinitionManager`** (`data/WispDefinitionManager.java`) is the single source of truth,
  held only on the logical server (including singleplayer's integrated server). It validates
  every create/edit/delete, persists to disk, and produces the sync payload sent to clients.
- **Server-authoritative networking** (`network/WispPayloads.java`, wired up in `WispMod.java`):
  the GUI never edits data directly — it sends a `RequestEditPayload` to the server, the server
  validates + checks permission, applies the change, then re-broadcasts the full definition set
  to every connected client via `SyncDefinitionsPayload`. New joiners get a full sync
  automatically.
- **Stable IDs, safe deletes:** `internalId` (e.g. `wisp_a1b2c3d4`) is generated once and never
  reused or changed. Deleting a Wisp is a soft-delete — it disappears from the GUI list, but any
  entity already placed with that id keeps working by falling back to the default definition
  (`WispDefinitionManager.getFallback()`) instead of breaking.

## The box → spawn → intro flow

1. Get a **Wisp Box** item (via the GUI's future "Give Box" action, or `/give @s wisp:wisp_box`
   in creative — you'll want to attach the `wisp:wisp_id` data component to target a specific
   custom Wisp; see `WispBoxItem.createFilledStack`).
2. Right-click a block with it → places a **`WispBoxEntity`** in the world (this is a real
   entity, not a block — matching the "sits on the ground, not a normal block" requirement).
3. Right-click the placed box → plays an opening sound/particles, then after a short animation
   window spawns the matching **`WispEntity`**, which immediately says one of its configured
   intro lines in chat to nearby players.
4. The Wisp floats in place, can be right-clicked again for an idle chat line, and persists
   across world reloads (`WispEntity` NBT stores only its `WispId`).

## The GUI — Wisp Creator

Press **G** in-game (rebindable in Controls) to open it. Left panel lists every non-deleted
Wisp plus a "+ Create New" button. Right panel edits the selected/new Wisp: name, color hex,
box name, box color hex, texture path, box texture path, with **Save / Reset / Duplicate /
Delete**. Hex fields show a live color swatch next to them.

## Textures — you provide these

The mod does **not** ship, generate, or bundle any custom Wisp artwork. Put your own PNGs here:

```
src/main/resources/assets/wisp/textures/verity/custom/<yourfile>.png
```

Then type that relative path (e.g. `textures/verity/custom/storm.png`) into the GUI's texture
field for that Wisp. If a referenced file doesn't exist, the entity renders with a safe
fallback instead of crashing — it won't corrupt the definition.

The base entity model/texture slots ship empty:
- `src/main/resources/assets/wisp/textures/entity/` — Wisp entity texture
- `src/main/resources/assets/wisp/textures/item/` — Wisp Box item icon
- `src/main/resources/assets/wisp/models/entity/`, `.../models/item/` — matching model JSONs

I did not add placeholder art beyond what's structurally required for the game not to crash
on a missing resource — you'll want to drop in real textures/models before playing.

## Multiplayer & permissions

- Singleplayer: the local player can always manage definitions (checked via
  `server.isSingleplayer()`).
- Dedicated server / LAN: requires permission level 2+ (operator). Enforced server-side in
  `WispMod#handleEditRequest` — the client-side GUI has no authority of its own.
- On join, every player receives a full definition sync automatically.

## What's stubbed / left for you

Given the size of this system, a few things are intentionally left as clear extension points
rather than fully fleshed out, so you can shape them to taste:
- **Rendering**: `WispEntity`/`WispBoxEntity` renderers aren't registered yet — you'll want to
  add `EntityRendererRegistry.register(...)` calls in `WispModClient` once you have real
  textures/models to point them at.
- **"Give Box" from the GUI**: `WispBoxItem.createFilledStack(WispDefinition)` exists and is
  ready to use — wire a button in `WispCreatorScreen` to give yourself a filled box for the
  Wisp you're editing.
- **Pickup capture**: sneak-right-clicking a Wisp currently discards it with a message; hook
  `WispBoxItem.createFilledStack` into that interaction if you want it to hand back an
  item instead.
- **Ability presets**: the ability system (`REGEN_AURA` / `SPEED_AURA` / `GLOW_AURA` /
  custom) is functional but only exposed via raw fields — a preset dropdown in the GUI is a
  straightforward addition on top of what's there.
- **Live GUI refresh**: the screen currently rebuilds its list from local data at `init()`;
  wiring a tick-based refresh after `SyncDefinitionsPayload` arrives would make multi-client
  editing feel instant rather than requiring a re-open.

## Remaining issues / what I could not verify

- **Not compiled/tested.** No network access in this sandbox meant I couldn't pull Minecraft
  1.21.11 + Yarn mappings + Fabric API to actually run `./gradlew build` or launch the game.
  The code follows current (1.21.x) Fabric API patterns (data components instead of raw NBT for
  item data, `CustomPayload` + `PayloadTypeRegistry` for networking, `DataTracker.Builder` for
  tracked data), but please treat first-build compiler errors as expected, not a sign something
  is fundamentally wrong — API surface details drift between 1.21.x point releases and I
  couldn't check against the exact 1.21.11 jar.
- Entity renderers and models aren't included (see above) — the mod will register/tick/network
  correctly but won't show a custom model until you add one.
