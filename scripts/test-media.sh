#!/usr/bin/env bash
# Offline media regression checks. No Gradle, no Android, no downloads, no device.
#
# Usage:
#   scripts/test-media.sh redgifs          pure RedGifs protocol helper tests (needs only a JDK)
#   scripts/test-media.sh gallery          gallery/RedGifs JSON helper tests; requires
#                                          JSON_JAR=/path/to/a/real/org.json-runtime.jar
#                                          (org/json/JSONObject.class + JSONArray.class).
#                                          Without it the mode prints BLOCKED and exits 3
#                                          (no stubs, no faked runtime).
#
# Compilation output goes to a private temp dir outside the repo which is left in place
# (path printed); this script deletes nothing.
#
# Note: the gallery-mode jar check only verifies that the class files are listed. It does not
# prove a working runtime — executing the tests does that, and an SDK-style stub jar will fail
# there honestly.
set -euo pipefail

usage() {
    echo "usage: $0 {redgifs|gallery}" >&2
}

if [ "$#" -ne 1 ]; then
    usage
    exit 2
fi

MODE="$1"
if [ "$MODE" != "redgifs" ] && [ "$MODE" != "gallery" ]; then
    usage
    exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# Compile/run output lives outside the repo so no build artifacts can land in Git.
OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/resync-media-tests.XXXXXX")"
echo "work dir (left in place): $OUT_DIR"

JAVAC_FLAGS=(--release 8 -Xlint:all -Werror -d "$OUT_DIR")

status=0

if [ "$MODE" = "redgifs" ]; then
    javac "${JAVAC_FLAGS[@]}" \
        "$REPO_ROOT/app/src/main/java/com/resync/app/net/thirdparty/RedgifsApi.java" \
        "$REPO_ROOT/tests/media/RedgifsApiTest.java" || status=$?
    if [ "$status" -eq 0 ]; then
        java -cp "$OUT_DIR" RedgifsApiTest || status=$?
    fi
    exit "$status"
fi

# gallery mode. Command substitution consumes the whole listing, so no pipe can SIGPIPE.
blocked=0
if [ -z "${JSON_JAR:-}" ] || [ ! -f "${JSON_JAR:-}" ]; then
    blocked=1
else
    jar_names="$(unzip -Z1 "$JSON_JAR" 2>/dev/null || true)"
    if [[ "$jar_names" != *"org/json/JSONObject.class"* ]] \
        || [[ "$jar_names" != *"org/json/JSONArray.class"* ]]; then
        blocked=1
    fi
fi
if [ "$blocked" -ne 0 ]; then
    echo "BLOCKED: real org.json runtime unavailable; gallery/JSON response tests NOT RUN"
    exit 3
fi

javac "${JAVAC_FLAGS[@]}" -cp "$JSON_JAR" \
    "$REPO_ROOT/app/src/main/java/com/resync/app/net/oauth/RedditGalleryResponse.java" \
    "$REPO_ROOT/app/src/main/java/com/resync/app/model/Gallery.java" \
    "$REPO_ROOT/app/src/main/java/com/resync/app/util/TextUtils.java" \
    "$REPO_ROOT/app/src/main/java/com/resync/app/util/WebsafeUtils.java" \
    "$REPO_ROOT/app/src/main/java/com/resync/app/net/thirdparty/RedgifsApi.java" \
    "$REPO_ROOT/app/src/main/java/com/resync/app/net/thirdparty/RedgifsResponse.java" \
    "$REPO_ROOT/tests/media/GalleryJsonTest.java" || status=$?
if [ "$status" -eq 0 ]; then
    java -cp "$OUT_DIR:$JSON_JAR" GalleryJsonTest || status=$?
fi
exit "$status"
