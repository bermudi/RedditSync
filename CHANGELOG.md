# Changelog

All notable changes to **ReSync** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

> **Lineage.** ReSync is a revival of the discontinued, closed-source app *Sync for Reddit*
> (by Laurence Dawson, last released as v23.06.30 in 2023 and abandoned after Reddit shut off
> third-party API access). This changelog begins at the revival; the original app's history is
> not included. Everything in `1.0.0` describes how ReSync differs from that final original build.

## [Unreleased]

### Fixed

- **Posts/comments not opening at all ("every post throws an error", albums included).** Reddit's
  edge now bot-walls every thread-shaped endpoint (`/comments/{id}.json`, `/r/{sub}/comments/…`)
  for non-browser HTTP clients, answering `403` with the web-app HTML shell instead of JSON —
  while listing endpoints still answer normally, which is why the feed kept working. Any reddit
  GET that comes back with that signature is now transparently re-fetched through the session
  WebView (a real browser engine with cookies that clears the edge challenge), and the JSON it
  returns is fed to the original parser. Comment threads, galleries/albums, "load more comments"
  and award views all ride this path.
- **Galleries/albums not opening ("Could not load gallery").** Reddit stopped answering `/api/info`
  for third-party clients — the endpoint Sync's album viewer resolves a gallery post through. The
  gallery is now loaded from `/comments/{id}.json`, which publishes the same post object
  (`gallery_data` + `media_metadata`), so albums open again.
- **"Load more comments" failing.** Reddit also blocked `/api/morechildren`. The request now reads
  the comments thread instead (`/comments/{link}` for a top-level "more",
  `/comments/{link}/_/{parent}` for a nested one) and cuts the reply back down to the flat list
  `morechildren` used to return, keeping the ordering the indentation is derived from.
- **"View awards" failing.** Same `/api/info` block: post awards load from the thread's first
  listing, comment awards from `/comments/{link}/_/{comment}` (the comment's link id is now passed
  through to the awards dialog).
- **RedGifs playback.** The `/ifr/<id>` embed-page scrape (the earlier workaround) now answers
  404. RedGifs is read through their stable v2 API instead: a temporary token from
  `/v2/auth/temporary` (cached for ~a day, tied to the app's user agent) unlocks
  `/v2/gifs/{id}`, whose `urls.hd`/`urls.sd` are the direct media files.
- **RedGifs/Gfycat link IDs parsed as empty** when the URL carried a query string or fragment
  around the id (`…/watch/<id>/?92/`), producing 404s.
- **Sign-in failing with a bare "token fetch failed".** The hidden token-minting WebView ran its
  same-origin fetch before the reddit.com page it needs had loaded — from the blank page the
  fetch is a null-origin request and dies on CORS. The mint now waits for the page to finish
  loading, retries once through the full home page when reddit's edge answers with a bot
  challenge (common from VPN exit IPs), and if login still fails the message names the actual
  reason (HTTP status + response snippet, or the fetch error) instead of the generic string.
- **Albums with a deleted image failing entirely.** A picture removed from a gallery leaves its
  id in `gallery_data` but is gone from `media_metadata`; the strict lookup aborted the whole
  album for it. The slot is now left blank like reddit's web client, and gif-only entries
  (no `u`/`mp4`) resolve through their `gif` source. A failing album now also names the actual
  reason (HTTP status + response snippet, or the parse error) in the toast instead of a fixed
  "Error loading album".
- **The app failing app-wide after a restart ("Error loading page" on everything).** The
  minted API token lived only in memory, so every cold start had to re-mint it — and reddit's
  edge has begun answering the token fetch with its 403 bot-challenge even for logged-in
  sessions, which could fail the mint and with it every request. The token is now persisted
  and survives restarts (re-mint ~once a day), and the mint always loads the full home page —
  the only page that executes the challenge JS that clears the block (observed live: fetch
  from a bare page 403s, home-page fetch mints within seconds).
- **RedGifs token rejection retries.** A RedGifs request turned away with a stale token now
  re-runs once with a fresh token instead of surfacing an error.
- **Threads failing to render when the text contains a `$`.** Matched text handed back as a regex
  replacement (fenced code blocks, named-link redaction, the selftext preview) was read as group
  references — silently corrupting the text or throwing. All such replacements are now literal.
- **Gfycat links dead.** Gfycat's domains no longer resolve; its content moved to RedGifs under
  the same slugs, so Gfycat requests now resolve through the RedGifs v2 API.
- **Imgur albums/images need no API key anymore.** They previously required building with an
  `YOUR_IMGUR_CLIENT_ID`; they are now read from Imgur's own pages (the album's embedded JSON,
  with the keyless `embed?pub=true` page as fallback), and single images are derived directly.
  Titled Imgur URLs (`imgur.com/gallery/some-title-words-<id>`) also resolve to the right id now
  instead of the first word of the title. (A key is still needed for Imgur **uploads**.)
- **Spoilers: "Missing post information".** Tapping a spoiler now reveals it where it sits
  instead of attempting a rewrite of the post body that no longer matched what was stored.
- **`/s/` share links.** Reddit's shortened share links now resolve to the post they point at
  and open in-app (an authorized HEAD reads the destination; failures fall back to the in-app
  browser).
- **Deprecated `/u/` profile endpoint** replaced with `/user/` everywhere.
- **Album "reopen where I left off"** no longer depends on rewriting the cached response body
  (which broke with the new response shapes); the position is kept beside the album instead.

## [1.0.3] - 2026-06-22

### Fixed

- **Crash when changing dropdown settings.** Tapping options like Autoplay, comment swipe actions, or
  the data-saving preload settings crashed the app or kicked you back to the previous screen. These now
  open a picker and save your choice correctly.
- **Comment and post swipe actions didn't work.** Swiping a comment (or post) to trigger a quick action
  did nothing. Swiping now works again.
- **"Autoplay on WiFi only" not playing.** WiFi detection used an outdated method that fails on newer
  Android, so WiFi-only autoplay never triggered. Reworked to detect the connection reliably.

## [1.0.2] - 2026-06-22

### Fixed

- **Reordering favorite subreddits.** You can again long-press a favorite in the drawer and drag it
  into the order you want. ([#2](https://github.com/X-15/ReSync/issues/2))
- **Crash when opening a subreddit's "About"** from the subreddit options menu.

## [1.0.1] - 2026-06-22

### Fixed

- **Missing post thumbnails in the card list views** (Compact / Smaller cards / Small cards). Reddit now
  returns HTML-escaped signed thumbnail URLs; left as-is, the escaped query string made the image CDN
  reject the request, so posts fell back to a plain type icon with no preview image. Thumbnails now load
  correctly. ([#1](https://github.com/X-15/ReSync/issues/1))

## [1.0.0] - 2026-06-22

First public release. The original app no longer functioned — its Reddit API key, image/preview
proxy, "Ultra" cloud backend, and in-app billing were all dead. ReSync rebuilds the app from clean
source and makes it work again entirely client-side, with the tracking and monetization removed.

### Added

- **Working sign-in.** A new login flow that authenticates against current Reddit and supports normal
  account features again (2FA/SSO accounts included). The original app's login no longer worked.
- **Anonymous / logged-out browsing.** Logged-out users can now read the front page, subreddit feeds,
  and search instead of hitting an error.
- **"Sign in" action in the toolbar overflow**, so login is reachable in the default casual layout
  (which has no navigation drawer).
- **Reddit Chat.** Built-in chat support. A "Chat" action appears in *More actions* when signed in and
  opens the rooms list and message threads.
- **Inline forum-style quote cards.** Reddit permalinks inside comments and self-text now render as
  collapsible cards showing the linked post/comment inline (author, subreddit, score, comment count,
  a clamped body, tap to expand, long-press to open the full thread) instead of plain links.
- **Client-side website/link previews.** The "website preview" boxes in comments and self-text work
  again via Open Graph (`og:image`) extraction with a real-WebView screenshot fallback, replacing the
  dead Sync image proxy. Favicons are rejected so a site icon isn't upscaled into a blur. Previews of
  the first ~40 comments are prefetched when a thread opens, so they appear already-loaded.
- **Bundled privacy policy & terms.** A local `legal.html` (privacy + terms for an unofficial Reddit
  client) replaces the original's dead iubenda link (`404`).
- **Developer Options are always visible** (previously gated behind specific developer usernames).

### Changed

- **Authentication backend** was replaced so the app works against current Reddit; the existing
  `oauth.reddit.com` REST layer is reused unchanged.
- **Username resolution.** The real username is now resolved at login, with a silent
  self-heal migration for installs that had stored a placeholder value.
- **Imgur albums/images** repointed from the dead `images.syncforreddit.com` proxy directly to the
  Imgur API.
- **RedGifs playback** rewritten to read the public `/ifr/<id>` embed page's `og:video` tag, because
  RedGifs changed their API (the old `urls.hd`/`urls.sd` fields are gone). The old token→IP→gif
  request chain was removed.
- **Unfetchable previews show nothing.** Website-preview and quote spans now start collapsed and
  expand only once content loads; a `404` / missing image / failed screenshot shows the plain link
  with no box, instead of a broken-image placeholder.
- **Preview work runs off a dedicated thread pool** so multi-second `og:image`/screenshot resolution
  no longer starves normal image loading.
- **Rebranded "Sync" → "ReSync"** across onboarding, settings, notifications, and preset names. The
  verb "sync" (comments / hidden posts / offline) was deliberately kept.
- **Application ID changed** from `com.laurencedawson.reddit_sync` to `com.resync.app` (so ReSync
  installs as its own app alongside anything else). The Reddit User-Agent updated to match.
- **GitHub-hosted artwork is now bundled in the APK** instead of hot-loaded from the original author's
  repository.
- **The original "Sync shuts down June 30 2023" notice** is no longer shown.

### Fixed

- **Native video and image-viewer crash** (`ArithmeticException: divide by zero`) when opening any
  video — caused by integer-division casts the decompiler had dropped. Restored across all affected
  sites.
- **Previews collapsing to blank on narrower screens** (e.g. Galaxy S23) from six more dropped
  float-casts causing integer-division height collapse.
- **Profile button returning 404/400** — fixed by resolving the real username at login.
- **Comment overflow ("3-dots") menu crash** (`Resources$NotFoundException`) — stale inlined resource
  IDs re-symbolized so they survive a rebuild.
- **Every bottom sheet crashing on Android 15** (`view is not associated with BottomSheetBehavior`) —
  fixed by disabling forced edge-to-edge on the Material theme overlays.
- **Gallery crash on close** (`FragmentManager has been destroyed`) under the newer SDK lifecycle.
- **Overflow menu crash on Android 16** (`GradientDrawable cannot be cast to AnimatedVectorDrawable`) —
  restored the 8 real animated checkbox drawables the original's resource shrinker had stubbed out.
- **Image-viewer "Direct" share** not opening the Android share sheet.
- **Account avatar and other features silently not working** — several reflective lookups baked into
  the decompiled source used stale minified names that failed against the real libraries; all fixed.
- **Infinite-scroll pagination** never loading page 2.
- **Reddit video downloads saving audio-only** files (case-sensitive DASH track matching after Reddit
  renamed the tracks).
- **Empty Inbox "Unread"/DMs** tabs (a null-handling crash) and an **intermittent image-viewer
  slide-to-close crash**.
- **Literal `null` text appearing in quote cards** (Android `optString` returning the string "null").

### Removed

- **All advertising** — the Amazon and AdMob/GMS ad SDKs, OMSDK, and every ad code path and consent
  flow.
- **In-app billing / purchasing** — the entire purchase, restore, and "remove ads / upgrade" surface,
  plus the billing dependency.
- **"Ultra" is unlocked for free** — all local premium features (previews, comment translation, AMP
  bypass, custom paints/tags, etc.) work without a purchase.
- **Crash reporting (Crashlytics)** and the unused **analytics** dependency.
- **Dead "Ultra cloud" calls** — license-confirm, catalog-download, and cloud backup/restore/delete
  that hit Sync's dead backend (and hung or errored) are neutralized and their UI hidden.
- **Original author identity** (`ljdawson` credits, dev badge, profile link, error-text mentions).
- **Dead request chains and stale baked-in artifacts** (the old RedGifs token/IP requests; stale ART
  baseline profiles carried in the original APK).

### Security

- **No telemetry.** With crash reporting and analytics removed, the app no longer reports crash or
  usage data. The bundled policy documents a no-data-collection, on-device-storage posture.
- **No third-party proxy dependency.** Previews, Imgur, and RedGifs were re-implemented to talk
  directly to `reddit.com`, `api.imgur.com`, and `redgifs.com`, removing all reliance on Sync's
  discontinued proxy hosts — and adding no new intermediary.
- **API keys stripped from source.** The published source contains no third-party API keys (the
  original app's keys are replaced with `YOUR_*` placeholders); see the README.

### Under the hood

*The original app shipped only as an obfuscated APK. These foundational changes made a readable,
buildable, modern project exist at all — not end-user-facing, but they are the bulk of the work.*

- **Reverse-engineered the release APK into a clean, buildable Gradle project** (`./gradlew
  assembleDebug`), decompiled with jadx and cross-checked against a Vineflower second pass.
- **Fully deobfuscated** every package, class, method, field, local variable, and anonymous class
  across the whole source tree — from ~4,500 obfuscated methods and ~5,200 fields down to zero — using
  descriptor-matching against the real libraries to recover ~11,000 names.
- **Replaced all decompiled library code with real Maven artifacts** (~88 dependencies at the original
  versions: AndroidX, Glide, ExoPlayer, Material, OkHttp, Volley, Otto, etc.), so you read the *app*,
  not decompiled libraries. A few Sync-modified libraries are kept as vendored source.
- **Upgraded the toolchain:** `compileSdk`/`targetSdk` 33 → 35, AGP 8.6, Gradle 8.7, JDK 17, with
  Android 14/15/16 compatibility fixes. Verified running on Galaxy S8 and S23.
- **Resource-pipeline and build hardening:** removed colliding duplicate framework attributes, fixed
  stale resource-ID literals, characterized the resource-shrinker drawable "tombstones" as harmless,
  and produce a non-debuggable build with a clean strict-dex merge.

[Unreleased]: https://github.com/X-15/ReSync/compare/v1.0.3...HEAD
[1.0.3]: https://github.com/X-15/ReSync/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/X-15/ReSync/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/X-15/ReSync/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/X-15/ReSync/releases/tag/v1.0.0
