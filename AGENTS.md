# ReSync repository notes

- Define project-specific terms in `GLOSSARY.md` before using them.
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
- Reddit galleries and Imgur albums have separate request paths. Diagnose each
  separately; an Imgur client ID does not fix Reddit gallery lookup.
- Sync Up is a patch bundle for original Sync APKs, not this renamed ReSync source.
- Do not start SDK, Gradle, dependency, or APK downloads without agreement on
  bandwidth. Do not push or pull on the operator's limited data plan.
