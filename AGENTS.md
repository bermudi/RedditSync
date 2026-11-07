# ReSync repository notes

- Define project-specific terms in `GLOSSARY.md` before using them.
- `RESEARCH.md` records the November 7, 2026 access-policy and recovery findings.
  Re-check dated external policies before treating them as current.
- README and BUILD status statements are upstream claims, not local test evidence.
  Do not describe an APK as working until the relevant flows have run.
- `WebSessionManager` changes authentication, not the app's dependency on Reddit
  endpoints. Browser-derived tokens are not proof of approved API access.
- Reddit galleries and Imgur albums have separate request paths. Diagnose each
  separately; an Imgur client ID does not fix Reddit gallery lookup.
- Sync Up is a patch bundle for original Sync APKs, not this renamed ReSync source.
- Do not start SDK, Gradle, dependency, or APK downloads without agreement on
  bandwidth. Do not push or pull on the operator's limited data plan.
