# Sync recovery findings

Research date: November 7, 2026. This is a findings record, not a build or test report.
Terms are defined in [GLOSSARY.md](GLOSSARY.md).

## Result

There is evidence of current fixes for Sync's album and additional-comment errors.
There is no verified working APK from this checkout yet. A personal-use app does not
automatically qualify for approved Reddit API access.

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
- No tracked CI workflow was found. No feature work or CI setup was started.

No build, app launch, device interaction, login, or live authenticated API request
was performed. No dependencies or APKs were downloaded. Existing README build/run
claims have not been independently verified.

## Inputs needed before implementation

- Phone model, Android version, and installed Sync variant/version, if any.
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
its authorization or API requests. The next configuration detail to obtain is
the registered redirect URI; it was not visible in the screenshot. The Android
version and installed Sync version, if any, are also still needed.

An existing usable client could support the original Sync patch route without
first creating a new API registration. Whether it is usable remains unverified.
The separate Devvit screenshot asks for an automated account; that screen is not
evidence of an external Android client's OAuth setup.

No app configuration, login, live API request, or build was performed in this
follow-up.
