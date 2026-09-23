# Paging3 + Compose media-grid "blink" repro

Minimal reproduction of GlideImage tiles blinking/reloading while scrolling a 3-column
`LazyVerticalGrid` backed by **Room + RemoteMediator (Paging3)** with placeholders enabled
and **content-based keys**.

## Versions (identical to the affected app)

| Component        | Version        |
|------------------|----------------|
| Gradle           | 8.13           |
| AGP              | 8.13.0         |
| Kotlin           | 2.1.20         |
| KSP              | 2.1.20-1.0.32  |
| compileSdk / targetSdk | 36       |
| minSdk           | 28             |
| Compose BOM      | 2026.06.01     |
| paging-runtime   | 3.3.6          |
| paging-compose   | 3.5.0          |
| Room             | 2.6.1          |
| Glide            | 5.0.5          |
| glide-compose    | 1.0.0-beta09   |

## Architecture (why Room + RemoteMediator matters)

- `Pager` is backed by a **Room `PagingSource`** (`MediaDao.observeAll()`).
- `MediaRemoteMediator` fabricates pages (with a simulated network delay) and `insertAll`s
  them into Room on each APPEND.
- `PagingConfig` does **not** set `enablePlaceholders`, so it defaults to `true`.

Every APPEND writes to Room, which **invalidates** the Room `PagingSource` and makes Paging
re-emit fresh `PagingData`. That re-emission is the trigger the bug needs — an in-memory
`PagingSource` never invalidates, so it cannot reproduce the blink.

## What it shows

A single screen renders a paged grid of remote images (`picsum.photos`). A switch at the top
flips the `LazyVerticalGrid` key strategy:

- **CONTENT key** (switch off, default) — `key = { peek(it)?.let { "${it.id}_${it.messageId}" } ?: index }`
  Same shape as `mediaItems.itemKey { "${item.id}_${item.messageId}" }`.
  **On-screen tiles blink / reload while scrolling.** On each Room re-emission `peek(index)`
  transiently returns `null`, so the key toggles between the content key and the placeholder
  index, disposing the tile composition and resetting GlideImage's loaded state.

- **INDEX key** (switch on) — `key = { index -> index }`
  No blink (keys are position-stable so compositions survive re-emission), but tiles shift on delete.

## Proof in logcat

Each tile has a `DisposableEffect` that logs on compose and dispose:

```
adb logcat -s BLINK
```

Scroll with the CONTENT key selected and you'll see `DISPOSE` / `compose` pairs for tiles
that stay on screen — that is the blink. With the INDEX key, those tiles are not disposed.

## Build & run (CLI)

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.pagingblink/.MainActivity
adb logcat -s BLINK
```

Requires network access for the remote images.
