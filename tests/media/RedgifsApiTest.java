import com.resync.app.net.thirdparty.RedgifsApi;

import java.util.Map;

/**
 * Offline regression tests for the pure RedGifs protocol helper. All fixtures are synthetic; they
 * exercise the source contract, not live RedGifs behavior. Plain main, no JUnit; assertions are
 * explicit so no -ea flag is needed.
 */
public final class RedgifsApiTest {

    private static final String UA = "sync-test-agent/0";

    private static int passes = 0;
    private static int failures = 0;

    private RedgifsApiTest() {
    }

    public static void main(String[] args) {
        metadataUrlAccepts();
        metadataUrlRejects();
        headerCases();
        mediaUrlPreference();
        mediaUrlRejects();
        refreshPolicy();
        System.out.println("redgifs: " + passes + " passed, " + failures + " failed");
        if (failures > 0) {
            System.exit(1);
        }
    }

    // 1. watch/ifr links, http input, case normalization, query/fragment/trailing slash.
    private static void metadataUrlAccepts() {
        check("watch link",
            "https://api.redgifs.com/v2/gifs/somecamelid".equals(RedgifsApi.metadataUrl("https://redgifs.com/watch/SomeCamelId")));
        check("ifr link on www",
            "https://api.redgifs.com/v2/gifs/abc123".equals(RedgifsApi.metadataUrl("https://www.redgifs.com/ifr/abc123")));
        check("http m-host with query and fragment",
            "https://api.redgifs.com/v2/gifs/id42".equals(RedgifsApi.metadataUrl("http://m.redgifs.com/watch/ID42?source=embed#frag")));
        check("one trailing slash trimmed",
            "https://api.redgifs.com/v2/gifs/id".equals(RedgifsApi.metadataUrl("https://redgifs.com/watch/id/")));
        check("uppercase host and scheme tolerated, id lowercased",
            "https://api.redgifs.com/v2/gifs/ab12".equals(RedgifsApi.metadataUrl("HTTPS://WWW.REDGIFS.COM/watch/ab12")));
        check("explicit https default port accepted",
            "https://api.redgifs.com/v2/gifs/ab".equals(RedgifsApi.metadataUrl("https://redgifs.com:443/watch/ab")));
        check("explicit http default port accepted and upgraded",
            "https://api.redgifs.com/v2/gifs/ab".equals(RedgifsApi.metadataUrl("http://redgifs.com:80/watch/ab")));
    }

    // 2. Rejections: lookalike host, userinfo, port, missing/malformed id, other paths.
    private static void metadataUrlRejects() {
        checkRejects("lookalike host", () -> RedgifsApi.metadataUrl("https://redgifs.com.evil.com/watch/abc"));
        checkRejects("suffix host", () -> RedgifsApi.metadataUrl("https://evilredgifs.com/watch/abc"));
        checkRejects("userinfo", () -> RedgifsApi.metadataUrl("https://user:pass@redgifs.com/watch/abc"));
        checkRejects("explicit port", () -> RedgifsApi.metadataUrl("https://redgifs.com:8443/watch/abc"));
        checkRejects("cross-scheme default port rejected (https on 80)", () -> RedgifsApi.metadataUrl("https://redgifs.com:80/watch/abc"));
        checkRejects("cross-scheme default port rejected (http on 443)", () -> RedgifsApi.metadataUrl("http://redgifs.com:443/watch/abc"));
        checkRejects("encoded separator at id end", () -> RedgifsApi.metadataUrl("https://redgifs.com/watch/abc%2f"));
        checkRejects("encoded separator inside id", () -> RedgifsApi.metadataUrl("https://redgifs.com/watch/abc%2Fdef"));
        checkRejects("double trailing slash rejected", () -> RedgifsApi.metadataUrl("https://redgifs.com/watch/abc//"));
        checkRejects("triple trailing slash rejected", () -> RedgifsApi.metadataUrl("https://redgifs.com/watch/abc///"));
        checkRejects("missing id", () -> RedgifsApi.metadataUrl("https://redgifs.com/watch/"));
        checkRejects("dashed id is not alphanumeric", () -> RedgifsApi.metadataUrl("https://redgifs.com/watch/abc-def"));
        checkRejects("encoded separator in id", () -> RedgifsApi.metadataUrl("https://redgifs.com/watch/abc%2Fdef"));
        checkRejects("percent-broken id", () -> RedgifsApi.metadataUrl("https://redgifs.com/watch/abc%zz"));
        checkRejects("non-watch/ifr path", () -> RedgifsApi.metadataUrl("https://redgifs.com/gifs/abc"));
        checkRejects("non-redgifs host", () -> RedgifsApi.metadataUrl("https://example.com/watch/abc"));
        checkRejects("garbage input", () -> RedgifsApi.metadataUrl("not a url"));
        checkRejects("null input", () -> RedgifsApi.metadataUrl(null));
    }

    // 3. Header maps: shared exact UA, no Reddit credentials, fresh independent maps, token rules.
    private static void headerCases() {
        Map<String, String> anon = RedgifsApi.headers(UA, null);
        check("anonymous map carries exact UA", UA.equals(anon.get("User-Agent")));
        check("anonymous map accepts json", "application/json".equals(anon.get("Accept")));
        check("anonymous map has no Authorization", !anon.containsKey("Authorization"));
        check("anonymous map has no extra headers", anon.size() == 2);

        Map<String, String> authed = RedgifsApi.headers(UA, "synthetic-token-ABC123");
        check("token map carries exact same UA", UA.equals(authed.get("User-Agent")));
        check("token map builds bearer header", "Bearer synthetic-token-ABC123".equals(authed.get("Authorization")));

        Map<String, String> first = RedgifsApi.headers(UA, "synthetic-token-ABC123");
        Map<String, String> second = RedgifsApi.headers(UA, "synthetic-token-ABC123");
        first.put("X-Mutated", "yes");
        check("header maps are fresh and independent", !second.containsKey("X-Mutated") && first.size() == 4);

        checkRejects("blank token", () -> RedgifsApi.headers(UA, ""));
        checkRejects("whitespace token", () -> RedgifsApi.headers(UA, " "));
        checkRejects("space inside token", () -> RedgifsApi.headers(UA, "abc def"));
        checkRejects("CRLF token", () -> RedgifsApi.headers(UA, "abc\r\ndef"));
        checkRejects("control character token", () -> RedgifsApi.headers(UA, "abc\u0000def"));
    }

    // 4. hd/sd preference, both fallback directions, exact filenames and queries preserved.
    private static void mediaUrlPreference() {
        String hd = "https://media.redgifs.com/SyntheticName-silent.mp4?rnd=7";
        String sd = "https://files.redgifs.com/SyntheticName-mobile.mp4";
        check("prefer hd picks hd", hd.equals(RedgifsApi.mediaUrl(hd, sd, true)));
        check("prefer sd picks sd", sd.equals(RedgifsApi.mediaUrl(hd, sd, false)));
        check("hd missing falls back to sd", sd.equals(RedgifsApi.mediaUrl(null, sd, true)));
        check("hd invalid falls back to sd", sd.equals(RedgifsApi.mediaUrl("http://media.redgifs.com/x.mp4", sd, true)));
        check("sd missing falls back to hd", hd.equals(RedgifsApi.mediaUrl(hd, null, false)));
        check("sd invalid falls back to hd", hd.equals(RedgifsApi.mediaUrl(hd, "https://media.redgifs.com/notmp4", false)));
        check("exact silent filename and query preserved",
            "https://media.redgifs.com/ExactCaseName-silent.mp4?expires=99&sig=abc".equals(
                RedgifsApi.mediaUrl("https://media.redgifs.com/ExactCaseName-silent.mp4?expires=99&sig=abc", null, true)));
        check("exact mobile filename preserved",
            "https://v3.redgifs.com/ExactCaseName-mobile.mp4".equals(
                RedgifsApi.mediaUrl(null, "https://v3.redgifs.com/ExactCaseName-mobile.mp4", true)));
    }

    // 5. Media URL validation: scheme/host/userinfo/port/path and HTML rejection.
    private static void mediaUrlRejects() {
        checkRejects("plain http rejected", () -> RedgifsApi.mediaUrl("http://media.redgifs.com/a.mp4", null, true));
        checkRejects("lookalike host rejected", () -> RedgifsApi.mediaUrl("https://media.redgifs.com.evil.com/a.mp4", null, true));
        checkRejects("userinfo rejected", () -> RedgifsApi.mediaUrl("https://user@media.redgifs.com/a.mp4", null, true));
        checkRejects("explicit port rejected", () -> RedgifsApi.mediaUrl("https://media.redgifs.com:8443/a.mp4", null, true));
        checkRejects("no extension rejected", () -> RedgifsApi.mediaUrl("https://media.redgifs.com/a", null, true));
        checkRejects("webm rejected", () -> RedgifsApi.mediaUrl("https://media.redgifs.com/a.webm", null, true));
        checkRejects("extension only in query rejected", () -> RedgifsApi.mediaUrl("https://media.redgifs.com/a?fmt=.mp4", null, true));
        checkRejects("html iframe string rejected", () -> RedgifsApi.mediaUrl("<iframe src=\"https://www.redgifs.com/ifr/x\"></iframe>", null, true));
        checkRejects("both missing rejected", () -> RedgifsApi.mediaUrl(null, null, true));
        check("cdn subdomain accepted",
            "https://media.redgifs.com/a.mp4".equals(RedgifsApi.mediaUrl("https://media.redgifs.com/a.mp4", null, false)));
        check("default 443 port accepted",
            "https://media.redgifs.com:443/a.mp4".equals(RedgifsApi.mediaUrl("https://media.redgifs.com:443/a.mp4", null, false)));
    }

    // 6. Refresh only on 401/403 of a first attempt; everything else terminal.
    private static void refreshPolicy() {
        check("401 first attempt refreshes", RedgifsApi.shouldRefresh(401, false));
        check("403 first attempt refreshes", RedgifsApi.shouldRefresh(403, false));
        check("401 after retry is terminal", !RedgifsApi.shouldRefresh(401, true));
        check("403 after retry is terminal", !RedgifsApi.shouldRefresh(403, true));
        check("404 is terminal", !RedgifsApi.shouldRefresh(404, false));
        check("429 is terminal", !RedgifsApi.shouldRefresh(429, false));
        check("500 is terminal", !RedgifsApi.shouldRefresh(500, false));
        check("no response (transport) is terminal", !RedgifsApi.shouldRefresh(-1, false));
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passes++;
            System.out.println("pass: " + name);
        } else {
            failures++;
            System.out.println("FAIL: " + name);
        }
    }

    private static void checkRejects(String name, Runnable call) {
        try {
            call.run();
        } catch (IllegalArgumentException expected) {
            check(name, true);
            return;
        }
        check(name + " (no IllegalArgumentException thrown)", false);
    }
}
