# Sync recovery findings

Research date: November 7, 2026. This is a findings record, not a build or test report.
Terms are defined in [GLOSSARY.md](GLOSSARY.md).

## Result

There is evidence of current fixes for Sync's album and additional-comment errors.
There is no verified working APK from this checkout yet. A personal-use app does not
automatically qualify for approved Reddit API access.

The operator has clarified the intended product: a working, personally
maintained source build that can be changed and extended later. A newly patched
original APK is not the desired end product. Earlier recommendations to prefer
binary patching optimized for short-term recovery and missed that goal.

Original APK inspection and external patch sources remain useful references for
source-level repairs. The 1.0.9 defects do not demonstrate that the reconstruction
approach itself cannot work. No change of authentication strategy, transplant of
another app's identity, source implementation, or replacement install has been
approved or performed in this clarification.

## Reddit access

- Sync was discontinued after Reddit's 2023 API pricing changes. This historical
  summary is also stated in Sync Up's maintainer README. The original developer's
  Reddit announcements were not successfully retrieved here.
- Reddit's own June 2023 statement says that, effective July 1, 2023, higher-volume
  third-party API use would cost $0.24 per 1,000 calls. It also listed a free limit
  of 100 queries per minute per OAuth client ID and said non-monetized apps below
  its usage threshold could remain free. This was usage-based app access, not a
  special fee charged only for users on Sync's free tier.
- The same historical statement announced restrictions on mature content effective
  July 5, 2023, and exemptions for some accessibility apps. None of this establishes
  that a new personal client is currently eligible for access.
- Reddit's developer FAQ, retrieved directly during this research, says that
  external Data API access has a separate approval process and terms, and access
  requires approval under the Responsible Builder Policy.
- Devvit hosts apps on Reddit. Its FAQ explicitly says that signing into Devvit
  does not provide external API credentials. It is not a drop-in Android-client
  replacement.
- Current public API closure dates and current approval availability remain
  unverified. The earlier chat update asserting October 31, 2026, January 12, 2027,
  and March 2027 dates was too firm and is withdrawn. Do not use those dates to
  make a decision.

Sources:

1. https://www.reddit.com/r/redditsync/comments/142dzy8/the_future_of_sync/
2. https://www.reddit.com/r/redditsync/comments/144jp3w/sync_will_shut_down_on_june_30_2023/
3. https://redditinc.com/news/apifacts
4. https://developers.reddit.com/docs/guides/faq

Items 3 and 4 were retrieved directly. Items 1 and 2 are historical reference
links, not successfully retrieved source text in this session.

The current Responsible Builder Policy page returned HTTP 403; its public
help-center JSON route returned HTTP 429. The Reddit migration-announcement URL
returned HTTP 403 on both normal and alternate public read routes. Reader
fallbacks did not recover those pages. Hosted web-search tool discovery returned
no available tool. A lightweight search attempt did not establish the closure
dates. This research therefore does not settle the current migration timetable.

## Album and comment fixes

The operator's linked thread could not be retrieved: direct/public read routes
and a reader fallback returned blocked-page responses. Its comments were not
verified. The independently retrieved Sync Up source addresses the same named
album symptom.

Sync Up's October 30, 2026 changelog records fixes for opening albums and loading
more comments in version 1.9.2. Its README, changelog, and patch metadata identify
version 1.9.4, with an October 31 security fix validating share-link destinations
before sending credentials. No separate claim about the current latest release
is made. The project's README warns that many patches are unverified.

The album-fix commit describes rejected `/api/info` and `/by_id` requests. Its
implementation gets the post from the thread endpoint and adapts the response for
the album reader. The comment-fix commit replaces rejected `/api/morechildren`
requests with thread/comment reads. These are maintainer findings and inspected
source, not live endpoint checks from this machine.

The patch metadata specifies `v23.06.30-13:39` for original free Sync. Pro and Dev
packages are also listed, but with no specific version in that metadata. This does
not verify compatibility with every APK, or with this renamed ReSync build.

Sources:

1. https://www.reddit.com/r/MorpheApp/comments/1xj42lb/reddit_sync_cannot_open_albums/
2. https://github.com/RashKash103/sync-up
3. https://raw.githubusercontent.com/RashKash103/sync-up/main/CHANGELOG.md
4. https://github.com/RashKash103/sync-up/releases/tag/v1.9.4
5. https://github.com/RashKash103/sync-up/commit/2a221826f270c91cd2455aaa9b3703124b64ed09
6. https://github.com/RashKash103/sync-up/commit/cda6ee7f2d0433eace9ea07e0832882f4c4c0b7a
7. https://raw.githubusercontent.com/RashKash103/sync-up/main/patches-list.json

The README, changelog, patch metadata, and the two linked commits' patch text were
retrieved directly. The specific release page is a reference link, not inspected
release-page evidence. No patch bundle or app binary was downloaded.

## Local checkout: observed, not run

- Current code identifies itself as ReSync 1.0.3, package `com.resync.app`.
- `auth/WebSessionManager.java` uses a WebView and browser cookies to request a
  token from `/svc/shreddit/token`, then uses `oauth.reddit.com`. The README's
  wording about working without the official API is therefore misleading:
  authentication changed, but the app still depends on Reddit endpoints.
- `net/oauth/OAuthGalleryRequest.java:28` still requests `/api/info.json`.
- `net/oauth/OAuthCommentsMoreRequest.java:68` still requests `/api/morechildren`.
  This matches the older request patterns addressed by Sync Up. Whether this
  checkout's browser-derived tokens encounter the same restrictions is unverified.
- `net/thirdparty/ImgurGalleryRequest.java:53` still supplies the placeholder
  `YOUR_IMGUR_CLIENT_ID`. Imgur albums are a separate path from Reddit galleries.
- JDK 17 and `adb` are installed. `~/Android/Sdk` contains emulator and system-image
  files, but the inspected SDK has no platform or build-tools directories.
- No default Gradle cache/wrapper distributions were found; `GRADLE_USER_HOME`
  is unset. The wrapper specifies Gradle 8.7. No tracked APK was found.
- No tracked CI workflow was found in this local snapshot. The later remote
  1.0.9 commit has a build workflow, inspected in the follow-up below. No feature
  work or CI setup was started.

No build, app launch, device interaction, login, or live authenticated API request
was performed. No dependencies or APKs were downloaded. Existing README build/run
claims have not been independently verified.

## Inputs needed before implementation

- Phone model and original Sync version are now supplied; authorized USB reads
  confirm Android 16. The app-info screenshot shows `v23.06.30-13:39`.
- One failing Reddit album link; an Imgur example too if that is also broken.
- Operator's choice: patch an original Sync APK, or work on this ReSync source.
  The patch route needs a matching unmodified APK; ReSync does not.
- For approved API access: evidence of an existing approved client or explicit
  permission from Reddit. Do not assume a new personal client can be registered.
- For the current ReSync route: acceptance that website-session access is
  unofficial, can break, and does not establish policy permission.
- For a source build: a build environment with Android platform 35, build tools,
  Gradle, and dependencies. Do not start large downloads on the limited data plan.
- Account login, if required for testing, must be entered by the operator inside
  the app. Never request passwords, cookies, access tokens, or signing keys in chat.

Possible recovery paths are not guarantees. Installation alone is insufficient:
feeds, login, Reddit galleries, additional comments, and required media must be
exercised before describing an APK as working.

## Follow-up: existing application registration

The operator supplied a screenshot showing an existing Reddit application with
the `installed app` type. Its client ID and account details are deliberately not
copied into this repository.

This establishes that a registration exists, not that Reddit currently accepts
its authorization or API requests. The first screenshot did not show the redirect
URI; the subsequent screenshot shows `http://redditsync/auth`. The value requested
by a patched app must match its registration if that registration is used.
Later evidence below supplies the original Sync version and Android version and
shows that the installed APK uses a different redirect.

An existing usable client could support the original Sync patch route without
first creating a new API registration. Whether it is usable remains unverified.
The separate Devvit screenshot asks for an automated account; that screen is not
evidence of an external Android client's OAuth setup.

No app configuration, login, live API request, or build was performed in this
follow-up.

## Follow-up: working original Sync and failed ReSync 1.0.9

### Operator evidence

The operator reports that their ReVanced-patched original Sync loads feeds,
images, and comments, but most videos and galleries fail. They also report that
ReSync 1.0.9 does not work adequately. These are operator observations, not device
tests performed by this agent.

Some working Reddit access is therefore reported. This does not prove that every
API endpoint works, that the registered client shown is the client being used,
or that a new login will succeed. New API registration or Devvit setup is not
the first diagnostic step for this reported media problem.

### Linked build: verified metadata and source

GitHub run `41551254214` completed successfully on November 3, 2026 at
00:16:16 UTC, for commit `3e00feb1d28f224844fda096b419422e02709b4d`.
Its workflow assembles debug/release APKs and uploads them. It does not install
the app or exercise feeds, login, galleries, or video playback.

The commit changes five lines in `OAuthCommentsRequest.java`, correcting which
web-page attributes its comments scraper reads. In the full file at that commit,
the scraper constructs post data with `is_video:false`, `media:null`,
`secure_media:null`, and `preview:null`; it sets a gallery flag without constructing
`gallery_data` or `media_metadata`. That path demonstrably supplies incomplete
media data. Its role in the operator's particular failures is unverified.

A successful build is therefore not evidence that ReSync 1.0.9 works as a client.
The local checkout is older (1.0.3); none of the remote changes were pulled.

Sources inspected through GitHub's API:

1. https://github.com/bermudi/RedditSync/actions/runs/41551254214
2. https://github.com/bermudi/RedditSync/commit/3e00feb1d28f224844fda096b419422e02709b4d
3. The workflow at that commit, obtained using the run's `path` field.
4. https://github.com/bermudi/RedditSync/blob/3e00feb1d28f224844fda096b419422e02709b4d/app/src/main/java/com/resync/app/net/oauth/OAuthCommentsRequest.java

### Original-Sync patch route

GitHub's latest-release API returned Sync Up `v1.9.4`, published October 31, 2026.
Its single listed patch-bundle asset is 1,059,981 bytes; no asset was downloaded.
The README pinned to that tag lists:

- `Fix opening albums`: replaces the old Reddit post-lookup request.
- `Fix Imgur links`: replaces Sync's retired Imgur proxy path.
- `Fix Redgifs API`: updates requests to that video host.
- `Fix loading more comments`: replaces the old additional-comments request.

Its `Fix video downloads` patch concerns downloaded files, not a general promise
to repair playback. Deleted media is not made available merely by patching.
The maintainer warns that not all patches are tested.

Sources:

1. https://github.com/RashKash103/sync-up/releases/tag/v1.9.4
2. https://github.com/RashKash103/sync-up/blob/v1.9.4/README.md
3. https://github.com/RashKash103/sync-up/blob/v1.9.4/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/sync/syncforreddit/api/SpoofClientPatch.kt

Earlier recommendation, now superseded by the clarified source-maintenance goal:
keep the existing install intact and evaluate a patched original Sync before
investing further in reconstructed ReSync. This was not implemented. The working
installed app remains useful as a fallback and behavior reference, not the
intended final product.

No APK was built, downloaded, patched, installed, or tested in this follow-up.
No account credentials were requested or used.

## Correction: installed ReVanced build's authentication is unknown

This section records the earlier uncertainty. The USB inspection below now
identifies the installed code's login mechanism.

The operator clarifies that their installed ReVanced-patched Sync does not use
their Reddit app token and suspects it uses a browser-style mechanism. Do not
treat the separate app registration as a dependency of this installed build.
The exact meaning of "app token" (public client ID versus private access token)
has not been independently established; no token value is needed.

The installed patch source, patch version, and APK have not been identified or
inspected. Generic patch documentation does not establish what that binary does.
A targeted GitHub code search for the Sync spoof-client patch in the current
official ReVanced repository returned no matches. It did not identify the
operator's installed patches or prove that a historical/forked patch is absent.
Hosted web-search tool discovery again returned no available tool.

Keep these mechanisms separate:

- Website-session authentication can obtain browser-derived credentials and
  still request structured API data.
- Website scraping reads page content and reconstructs app data. ReSync 1.0.9's
  comments fallback demonstrably does this, with incomplete media fields.

Browser-like behavior does not alone establish scraping, and similar symptoms
do not establish identical implementations. The installed app's authentication
and the cause of its media failures remain unverified.

The operator confirms they applied the patches on the phone with ReVanced and
do not have the patch files. Do not require retained patch files or assume there
is a prepatched download link.

The useful next input is a copy of the installed Sync APK. Its patched code can
be inspected without changing the installed app or requiring the original patch
files. Do not request account tokens, cookies, or a private app-data export.
No phone connection, export, app, or registration was accessed or changed.

## Authorized USB inspection of original Sync

### Acquisition and limits

After the operator indicated USB debugging was enabled, one authorized device
was visible. Read-only property queries confirmed Android 16 and arm64-v8a.
The screenshot identifies original Sync `v23.06.30-13:39`.

`pm path com.laurencedawson.reddit_sync` returned one installed APK. Only that
installation file was copied over USB:

- Private local copy: `/tmp/redditsync-installed.ItJo2t/sync-installed.apk`
- Size: 73,361,759 bytes.
- SHA-256: `e59940dc6557aaa7f524dc5fcd8d2ebd3c740d5206fdbf72f9e8b60f5da93b18`

No app data, preferences, login cookies, account tokens, or device logs were read.
No app was opened, stopped, installed, uninstalled, patched, or cleared. No remote
dependency or binary was downloaded. The APK is not tracked by Git.

### Login: static evidence

The installed APK uses a native OAuth flow with patched client identification:

- `classes3.dex`, `Lb9/a;`: exchanges an authorization code through
  `https://www.reddit.com/api/v1/access_token`.
- `Lb9/c;` and `Lb9/d;`: refresh-token requests through the same endpoint.
- `classes4.dex`, `Lw8/b;->c()`: returns a patched fixed Basic authorization
  header before the original construction code. Its value was redacted.
- `Lw8/b;->f()`: returns a `redreader://` redirect before the now-unreachable
  original `http://redditsync/auth` return.
- `classes2.dex`,
  `Lcom/laurencedawson/reddit_sync/singleton/a;->g()`: returns a user agent
  identifying RedReader.

Across all five DEX files, no markers for `/svc/shreddit/token`, `shreddit-post`,
`shreddit-comment`, or `WebSessionManager` were found. Browser user-agent strings
do exist in unrelated libraries and generic web components; they do not establish
the app's Reddit login mechanism.

The code therefore supports a RedReader-style OAuth identity, not ReSync's
website-token and comments-scraping implementation. This does not prove which
remote app owns the embedded client identifier, that a fresh login currently
works, or that this use has approval. No embedded authorization value was printed
or copied into this report.

### Gallery: static evidence and likely diagnosis

`classes3.dex`, `Ly8/q;->b(String)`, still constructs the Reddit gallery lookup
using `/api/info.json` and a post ID. Its response parser expects `gallery_data`
and `media_metadata`. Another old lookup appears in `classes4.dex`, `Ly8/c;`.

This matches the old request pattern addressed by Sync Up's album fix. Together
with the operator's screenshot showing an album error after a thumbnail loads,
it is a strong diagnostic lead. The specific failing request and its HTTP status
were not captured, so this is not a live confirmation of the server-side cause.

### Video: do not assume the patches are absent

The inspection found application call sites, not merely bundled class names:

- `classes4.dex`, `La8/a;->k()`, calls
  `FixRedgifsApiPatch.install(OkHttpClient.Builder)`.
- `classes.dex`, `Lr8/g;->parseNetworkResponse(...)`, calls
  `FixRedditVideoDownloadPatch.getLinks(...)`.
- Share-link resolver hooks are also wired in application code.

The RedGifs hook installs a request interceptor and contains a temporary-token
request to that host. Whether it succeeds for a failing video is unverified.
The Reddit video-download hook is not proof that playback works.

A failing video permalink is still needed to identify the host and exact path.
Do not claim all video failures share the gallery cause.

### Inspection evidence and tool failures

The standard `apkanalyzer` command failed because this SDK lacks build tools.
The bundled baksmali CLI also failed with a JCommander method mismatch.
Neither was repaired by downloading dependencies.

Instead, a temporary offline Java inspector used the SDK's existing dexlib2
library. It emits method/call names and allowlisted marker labels, while redacting
other string literals and suppressing field values. This is static analysis of
the real extracted APK, not a simulated app test.

Local evidence files beside the APK:

- `audit-inventory-final.txt`: whole-APK marker inventory and active patch calls.
- `audit-auth-profile-redacted.txt`: redacted login-method instructions.
- `audit-details-redacted.txt`: redacted token, gallery, and RedGifs instructions.
- `DexAudit.java` and `DexAudit.class`: the temporary inspection helper.

To re-open the relevant code without accessing the phone or printing credentials:

```sh
cd /tmp/redditsync-installed.ItJo2t && java -Xmx512m \
  -cp ".:$HOME/Android/Sdk/cmdline-tools/latest/lib/apkanalyzer-classpath.jar" \
  DexAudit sync-installed.apk 'Lw8/b;' 'Ly8/q;'
```

No replacement APK has been produced, and no media playback or authenticated
network request was tested.

## Sync patch source availability despite the upstream block

### Verified original repository status

GitHub's repository API for `ReVanced/revanced-patches` returned HTTP 451,
`Repository access blocked`, with `block.reason` set to `dmca`.
The response links to:

https://github.com/github/dmca/blob/master/2026/03/2026-03-12-morpheapp.md

The linked notice itself was not analyzed, and no conclusion about the legal
scope of the notice is made here. The operator's GitLab mirror was not inspected
because no specific mirror URL was supplied.

### Available source references, not just patch bundles

1. **Patcheddit**, `wchill/patcheddit`: repository metadata and actual directory
   listings confirm Sync-specific patch definitions under
   `patches/src/main/kotlin/app/morphe/patches/reddit/customclients/sync/syncforreddit`.
   Its fixes include login, API lookups, more comments, RedGifs, share links,
   thumbnails, user endpoints, and video downloads. Runtime Java sources also
   exist under `extensions/syncforreddit/src/main/java/app/morphe/extension/syncforreddit`.

   https://github.com/wchill/patcheddit

2. **Sync Up**, `RashKash103/sync-up`: its README explicitly describes the initial
   Sync-only import from Patcheddit as a faithful copy, followed by additional
   Sync-specific work. That is a maintainer provenance statement, not an
   independent byte-for-byte comparison. Both the patch definitions and runtime
   source directories were verified.

   https://github.com/RashKash103/sync-up

   Concrete gallery implementation, verified at tag `v1.9.4`:

   https://github.com/RashKash103/sync-up/blob/v1.9.4/extensions/syncforreddit/src/main/java/app/morphe/extension/syncforreddit/http/posts/FixPostLookupPatch.java

   Shared RedGifs implementation and token manager were also located, after an
   initial read used the wrong directory and returned HTTP 404. The correct
   directory includes `shared/library`:

   https://github.com/RashKash103/sync-up/tree/v1.9.4/extensions/shared/library/src/main/java/app/morphe/extension/shared/fixes/redgifs

3. **Community ReVanced-compatible Sync source**,
   `TheLastWallaby/revanced-redditsync`: its README targets the same original
   free Sync version, `v23.06.30-13:39`, and says official Sync patches are
   included from its version 0.7.0 onward. Directory listings confirm
   `src/patches/OfficialSyncPatches.kt`, `SyncCompatPatches.kt`, and Sync runtime
   Java classes, including RedGifs and video-download helpers.

   https://github.com/TheLastWallaby/revanced-redditsync

   https://github.com/TheLastWallaby/revanced-redditsync/blob/main/src/patches/OfficialSyncPatches.kt

   This is an unofficial compatibility project, not an authenticated copy of the
   exact historical ReVanced bundle installed on the operator's phone. Its
   README explicitly says Manager-side patching is not maintainer-tested.

GitHub metadata identifies all three repositories as non-archived and reports
GPL-3.0 license identifiers. No licensing assessment or code import was performed.
Two targeted historical-path queries in Patcheddit returned no commits, so no
claim is made that the original ReVanced history was recovered.

### What this means for the source build

Patch-definition Kotlin describes changes to an APK, while extension Java
supplies runtime behavior. A source-based repair can translate the relevant
changes into normal source edits; it does not require keeping a binary patcher
in ReSync's build process.

One concrete difference was inspected: Sync Up's RedGifs subclass at `v1.9.4`
adds handling for the removed `/info` request, while the installed APK's subclass
only installs the inherited interceptor and supplies a user agent. This is
additional reference logic to examine, not a live diagnosis of every video
failure.

The exact old patch bundle is therefore not a prerequisite for continuing the
source-first repair. Existing source references plus the inspected installed APK
provide code-level evidence. None of these findings proves a repaired APK works.

Only small metadata, source-file, and history reads were made. No clone, patch
bundle, dependency, or additional APK was downloaded; no app code was changed.

## Source-repair execution request and launch gate

The operator authorized starting the source repair with Fusion, using the
`piz` harness and `glm-5.3`. Both the Herdr and piz launch tools are installed.
The installed piz help identifies it as a ZCode-core client with model selection.

The lead is not in a Herdr-managed pane and no explicit Herdr connection was
supplied. The local Herdr skill requires an explicit session/socket selection
before controlling panes from outside Herdr. The model's provider also has not
been selected or verified. No helper session, pane, worktree, or app edit was
started while those choices are pending.

There is a separate final-build gate: the project requires CI-only builds but
currently prohibits pushes and large downloads. Permission for a feature-branch
push and the resulting APK download is needed before that validation/delivery
step. Nothing was pushed, pulled, or built during this launch check.
