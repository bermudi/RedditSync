# ReSync repository notes

- Define project-specific terms in `GLOSSARY.md` before using them.
- The operator's goal is a personally maintained, editable source build of Sync,
  including future feature changes. Do not substitute a patched original APK as
  the end product. Use original APK inspection and patch sources as references
  for source-level fixes. Keep the installed original client as a fallback.
- ReSync 1.0.9's failure is evidence against its current implementation, not
  evidence that a source-based reconstruction is infeasible. Authentication
  strategy still needs verification and an explicit decision; do not silently
  copy a different app's client identity into the source build.
- `RESEARCH.md` records the November 7, 2026 access-policy and recovery findings.
  Re-check dated external policies before treating them as current.
- README and BUILD status statements are upstream claims, not local test evidence.
  Do not describe an APK as working until the relevant flows have run.
- This local code snapshot is 1.0.3. A later remote 1.0.9 build was inspected by
  commit without pulling. Inspect the exact linked commit when discussing that
  build; local source does not represent it. Its workflow builds/uploads APKs
  without testing them on a device.
- `WebSessionManager` changes authentication, not the app's dependency on Reddit
  endpoints. Browser-derived tokens are not proof of approved API access.
- Do not infer an installed patched app's authentication from its ReVanced label
  or a separate app-registration screenshot. Identify its patch source/version
  or inspect the APK. Website-session authentication and website scraping are
  different mechanisms; one does not establish the other.
- The operator's original Sync APK was copied over authorized USB for static
  inspection. It has standard OAuth token requests, a patched fixed client
  authorization header, a RedReader-scheme redirect, and a RedReader user agent.
  Its gallery request still builds `/api/info.json`; RedGifs and video-download
  hooks are already wired. These are code findings, not live network tests.
- Keep that APK and all embedded credential values outside Git and chat. Do not
  read app data or logs to investigate login; the private installation-file copy
  is sufficient for static inspection.
- Reddit galleries and Imgur albums have separate request paths. Diagnose each
  separately; an Imgur client ID does not fix Reddit gallery lookup.
- Sync Up is a patch bundle for original Sync APKs, not this renamed ReSync source.
- The original ReVanced GitHub repository returned HTTP 451 with a DMCA block.
  Sync source remains available in Patcheddit, Sync Up, and the community project
  `TheLastWallaby/revanced-redditsync`. See the source inventory in `RESEARCH.md`.
  Do not call these an authenticated archive of the operator's exact patch release.
  Inspect runtime extension code as well as patch definitions when porting to source.
- Do not start SDK, Gradle, dependency, or APK downloads without agreement on
  bandwidth. Do not push or pull on the operator's limited data plan.
- Reddit gallery media is fetched from the thread endpoint via
  `net/oauth/RedditGalleryResponse`; the media cache stores a thread JSON array with a
  `position` int on the first listing object, not the old `/api/info` object. RedGifs
  temporary-token and gif-metadata requests must carry the same User-Agent
  (`RedgifsApi.headers`); tokens are per-resolution and never cached or logged. Never log
  tokens, raw media/token responses, or full media URLs; media boundaries log fixed
  phase/status/count messages via `android.util.Log`.
- `scripts/test-media.sh` offline checks verify pure helper contracts only. They cannot
  stand in for Android/Volley compilation, live endpoint behavior, or device evidence.
- Preview image transformations must match the requested `ImageView.ScaleType`
  (`FIT_CENTER -> fitCenter()`, `CENTER_CROP -> centerCrop()` in `CustomImageView.load`).
  A circular bitmap transformation belongs only to explicit avatar/icon callers (e.g. the
  tabbed-comments chip); wrapper rounded corners (`shape_round_corners`/`shape_circular`
  attrs) are a separate outline mechanism and must not be conflated with bitmap crops.
