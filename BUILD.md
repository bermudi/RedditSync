# Building ReSync

This guide covers building ReSync from source, including the optional third-party API keys that were
stripped from this repository.

## Prerequisites

- **JDK 17** (the build targets Java 17).
- **Android SDK** with:
  - Platform / `compileSdk` **35**
  - Build-tools **34+**
- A `local.properties` file in the repo root pointing at your SDK, e.g.:
  ```properties
  sdk.dir=C:\\Android\\Sdk          # Windows
  # sdk.dir=/home/you/Android/Sdk   # Linux
  # sdk.dir=/Users/you/Library/Android/sdk   # macOS
  ```
  (`local.properties` is git-ignored — it's machine-specific.)

## Build

```bash
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

The output APK is **debug-signed** (Gradle's auto-generated debug keystore). That's fine for
sideloading. If you publish your own builds and want users to be able to update in place, create a
stable release keystore and add a `signingConfig` — otherwise each machine's debug key differs and
installs collide on signature mismatch.

The app installs as `com.resync.app` (minSdk 23 / Android 6.0+).

---

## API keys

The original app's third-party keys are **not** included in this repository — they're replaced with
`YOUR_*` placeholder strings. **The app builds and the core Reddit experience works without changing
anything** (browsing, comments, voting, search, media, RedGifs, and chat all work through the app's
own sign-in, which needs no key). The keys below only re-enable a few optional integrations.

To add a key, open the listed file(s) and replace the placeholder string with your value.

| Placeholder | Needed for | Required? |
|---|---|---|
| `YOUR_YOUTUBE_DATA_API_KEY` | In-app YouTube playback | Optional |
| `YOUR_IMGUR_CLIENT_ID` | Imgur album/image viewing | Optional |
| `YOUR_RAPIDAPI_KEY` | Imgur **uploads** | Optional |
| `YOUR_REDDIT_CLIENT_ID` | Legacy Reddit OAuth login | **Not needed** (app uses a different sign-in) |

### YouTube Data API key

Without it, YouTube links open in the external YouTube app instead of the in-app player.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/), create (or pick) a project.
2. **APIs & Services → Library →** enable **"YouTube Data API v3"**.
3. **APIs & Services → Credentials → Create credentials → API key.** (Optionally restrict it to the
   YouTube Data API and to your app's package + signing certificate.)
4. Replace `YOUR_YOUTUBE_DATA_API_KEY` in:
   - `app/src/main/java/com/resync/app/ui/activities/CustomYouTubeVideoActivity.java` (2 occurrences)
   - `app/src/main/java/com/resync/app/dialogs/UrlSelectionDialogBottomSheet.java`

### Imgur Client-ID

Without it, Imgur albums/images won't load via the Imgur API.

1. Register an application at <https://api.imgur.com/oauth2/addclient>.
2. Choose **"OAuth 2 authorization without a callback URL"** (anonymous usage — you only need the
   **Client ID**, not the secret).
3. Replace `YOUR_IMGUR_CLIENT_ID` in:
   - `app/src/main/java/com/resync/app/net/thirdparty/ImgurGalleryRequest.java`
   - `app/src/main/java/com/resync/app/net/thirdparty/ImgurSingleImageRequest.java`
   - `app/src/main/java/com/resync/app/net/thirdparty/UploadImgurRequest.java`

### RapidAPI key (Imgur uploads only)

Only needed if you want to **upload** images to Imgur from the app. The upload path goes through the
Imgur API hosted on RapidAPI.

1. Sign up at <https://rapidapi.com/>, then subscribe to the **Imgur** API
   (`imgur-apiv3.p.rapidapi.com`).
2. Copy your RapidAPI key (`X-RapidAPI-Key`).
3. Replace `YOUR_RAPIDAPI_KEY` in:
   - `app/src/main/java/com/resync/app/net/thirdparty/UploadImgurRequest.java`

### Reddit OAuth client ID — not required

`YOUR_REDDIT_CLIENT_ID` in `app/src/main/java/com/resync/app/auth/OAuthLoginHelper.java` is part of the
**legacy** OAuth-app login flow, which Reddit retired. ReSync uses a different sign-in path instead, so
this is unused — you can leave the placeholder as-is.

### Firebase — not required

The original app's `google-services.json` and Firebase keys are intentionally absent. The Firebase
backend (push, remote config, "Ultra" cloud) is dead, and the app runs fine without it. You do **not**
need to add Firebase config to build or use ReSync.

---

## Offline media checks (no Gradle)

`scripts/test-media.sh` runs offline regression mains from `tests/media/` against the real source
helpers; it never builds the app or touches a device.

```bash
scripts/test-media.sh redgifs   # pure RedGifs protocol rules; needs only a JDK
scripts/test-media.sh gallery   # gallery/RedGifs JSON helpers; needs a real org.json runtime
```

The `gallery` mode requires `JSON_JAR` to point at a jar containing a **working `org.json`
runtime for the JVM** (implementation classes, e.g. the `org.json:json` artifact). An Android
SDK `android.jar` does **not** work: it holds API **stub** classes whose methods just throw, not
a runnable implementation, so tests would fail at execution. The script only preflights that
`org/json/JSONObject.class` and `org/json/JSONArray.class` are listed in the jar; actual
execution is what proves the runtime works. Without a jar the mode prints
`BLOCKED: real org.json runtime unavailable; gallery/JSON response tests NOT RUN` and exits 3;
there are no stubs or faked runtimes. Nothing is downloaded, no build artifacts land in the
repository, and the private temp compile dir is printed and left in place (nothing is deleted).
