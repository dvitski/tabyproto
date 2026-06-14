# Animation Types Design

Date: 2026-06-14

## Summary

Introduce two tiers to the animation API:

- **`RawAnimation`** — wire-level primitive; the individual animation IDs the device understands
- **`Animation`** — domain-level sealed class; the compiled playback concept (once or intro→loop)

Both are public API. `RawAnimation` is for callers who need direct device control or want to build custom `Animation` instances. `Animation` is for callers who want correct playback behavior without knowing the wire protocol.

---

## `RawAnimation` (enum, public API)

Rename current `Animation` enum to `RawAnimation`. Each entry holds only its device ID. Add missing entries from the official registry: `WORKSPACES_LOOP`, `WOW`, `YEAH`.

```kotlin
enum class RawAnimation(val id: String) {
    ANGRY_01_LOOP("angry_01_loop"),
    CALENDAR_IN("calendar_in"),
    CALENDAR_LOOP("calendar_loop"),
    // ... all entries
    WORKSPACES_LOOP("workspaces_loop"),
    WOW("wow"),
    YEAH("yeah"),
}
```

No `loops` or `next` properties on `RawAnimation` — those concerns belong on `Animation`.

---

## `Animation` (sealed class, public API)

Two variants encoding the two genuinely different playback behaviors:

```kotlin
sealed class Animation {
    abstract val id: String
    abstract val displayName: String

    /** Plays once and stops. */
    data class Once(
        override val id: String,
        override val displayName: String,
        val raw: RawAnimation,
    ) : Animation()

    /** Plays an optional intro, then loops the body indefinitely until stopped. */
    data class Looping(
        override val id: String,
        override val displayName: String,
        val intro: RawAnimation?,
        val body: RawAnimation,
    ) : Animation()
}
```

### Registry

A top-level `val Animations` object (or companion) holds all known `Animation` instances derived from the official `@hey-taby/animation-registry` data. Callers reference `Animations.BUSY_LOOP`, `Animations.CALENDAR_IN`, etc.

```kotlin
object Animations {
    val ANGRY_01_LOOP = Animation.Looping("angry_01_loop", "Angry 01 Loop", intro = null, body = RawAnimation.ANGRY_01_LOOP)
    val CALENDAR_IN   = Animation.Looping("calendar_loop", "Calendar", intro = RawAnimation.CALENDAR_IN, body = RawAnimation.CALENDAR_LOOP)
    val TASK_COMPLETED = Animation.Once("task_completed", "Task Completed", raw = RawAnimation.TASK_COMPLETED)
    // ...

    val all: List<Animation> = listOf(ANGRY_01_LOOP, CALENDAR_IN, TASK_COMPLETED, /* ... */)

    fun byId(id: String): Animation? = all.firstOrNull { it.id == id }
}
```

**Naming convention for `Looping` entries:** the canonical `id` is the body/loop animation's ID (e.g. `calendar_loop`), not the intro's. Callers identify an animation by what it *is*, not how it enters.

---

## `TabySession` changes

Add an overload that accepts the domain type:

```kotlin
suspend fun play(animation: Animation): CommandResult = when (animation) {
    is Animation.Once    -> play(animation.raw)
    is Animation.Looping -> when (val intro = animation.intro) {
        null -> play(animation.body)
        else -> play(AnimationCommand(intro, animation.body))
    }
}
```

Existing `play(RawAnimation)` and `play(AnimationCommand)` overloads stay — they're still valid for direct device control.

---

## `AnimationCommand` rename

`AnimationCommand` currently takes `Animation` (the old enum). Update to take `RawAnimation`.

```kotlin
data class AnimationCommand(val from: RawAnimation, val to: RawAnimation)
infix fun RawAnimation.then(next: RawAnimation) = AnimationCommand(this, next)
```

---

## Desktop (`Sidebar.kt`) changes

Driven by `Animation` sealed type:

| Type | Behavior |
|------|----------|
| `Once` | Play video once; hide on finish |
| `Looping`, no intro | `playerState.loop = true`; play body video |
| `Looping`, with intro | Play intro video once; on finish, swap to body with `loop = true` |

`AnimationGrid` passes `Animation` objects (from `Animations.all`) instead of `RawAnimation` entries. Send button calls `session.play(animation: Animation)`.

---

## Migration

- `Animation` (enum) → `RawAnimation` — rename throughout; fix all call sites
- Old `session.play(Animation)` → `session.play(RawAnimation)` at call sites that need the raw primitive, or migrate to `session.play(Animation)` (sealed) for the higher-level API
- `AnimationGrid` currently uses `Animation.entries` → replace with `Animations.all`
