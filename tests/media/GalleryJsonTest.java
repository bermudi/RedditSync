import com.resync.app.model.Gallery;
import com.resync.app.net.oauth.RedditGalleryResponse;
import com.resync.app.net.thirdparty.RedgifsResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Offline regression tests for the gallery/RedGifs JSON boundary helpers, run against a real
 * org.json runtime supplied via JSON_JAR. All fixtures are synthetic thread-array responses; they
 * exercise the source contract, not live Reddit or RedGifs behavior. Plain main, no JUnit;
 * assertions are explicit so no -ea flag is needed.
 */
public final class GalleryJsonTest {

    private static final String THREAD_URL = "https://oauth.reddit.com/comments/abc123.json?raw_json=1&limit=0&depth=1";

    private static int passes = 0;
    private static int failures = 0;

    private interface Call {
        void run() throws Exception;
    }

    private GalleryJsonTest() {
    }

    public static void main(String[] args) throws Exception {
        requestUrlCases();
        requestUrlRejects();
        parseOrderedGallery();
        sourceFallbackCases();
        previewAndCaptionCases();
        parseSkips();
        parseEmptyCases();
        parseStructuralErrors();
        structuralTypeCases();
        crosspostFallback();
        positionCases();
        withPositionRoundTrip();
        tokenJsonCases();
        mediaJsonCases();
        System.out.println("gallery: " + passes + " passed, " + failures + " failed");
        if (failures > 0) {
            System.exit(1);
        }
    }

    // 1. URL canonical cases.
    private static void requestUrlCases() {
        check("subreddit comments permalink with gallery query",
            THREAD_URL.equals(RedditGalleryResponse.requestUrl("https://www.reddit.com/r/testsub/comments/abc123/some_title/?is_gallery=true")));
        check("bare comments path, uppercase id lowercased",
            THREAD_URL.equals(RedditGalleryResponse.requestUrl("https://reddit.com/comments/ABC123")));
        check("old reddit gallery path",
            THREAD_URL.equals(RedditGalleryResponse.requestUrl("https://old.reddit.com/gallery/abc123")));
        check("new reddit permalink with .json on id",
            THREAD_URL.equals(RedditGalleryResponse.requestUrl("https://new.reddit.com/r/sub/comments/abc123.json/x/y")));
        check("query and fragment ignored",
            THREAD_URL.equals(RedditGalleryResponse.requestUrl("https://www.reddit.com/gallery/abc123?utm=x#frag")));
        check("explicit https default port accepted",
            THREAD_URL.equals(RedditGalleryResponse.requestUrl("https://www.reddit.com:443/r/testsub/comments/abc123/x")));
        check("trailing empty title segments tolerated after the id",
            THREAD_URL.equals(RedditGalleryResponse.requestUrl("https://www.reddit.com/comments/abc123//")));
    }

    private static void requestUrlRejects() {
        checkRejects("null url", () -> RedditGalleryResponse.requestUrl(null));
        checkRejects("garbage url", () -> RedditGalleryResponse.requestUrl("not a url"));
        checkRejects("plain http rejected", () -> RedditGalleryResponse.requestUrl("http://www.reddit.com/r/x/comments/abc123"));
        checkRejects("non-reddit host", () -> RedditGalleryResponse.requestUrl("https://example.com/r/x/comments/abc123"));
        checkRejects("unlisted subdomain", () -> RedditGalleryResponse.requestUrl("https://i.reddit.com/r/x/comments/abc123"));
        checkRejects("userinfo rejected", () -> RedditGalleryResponse.requestUrl("https://u:p@www.reddit.com/r/x/comments/abc123"));
        checkRejects("explicit port rejected", () -> RedditGalleryResponse.requestUrl("https://www.reddit.com:8443/r/x/comments/abc123"));
        checkRejects("non-base36 id rejected", () -> RedditGalleryResponse.requestUrl("https://www.reddit.com/r/x/comments/ab_c"));
        checkRejects("missing id rejected", () -> RedditGalleryResponse.requestUrl("https://www.reddit.com/r/x/comments/"));
        checkRejects("old /api/info style rejected", () -> RedditGalleryResponse.requestUrl("https://www.reddit.com/api/info.json?id=t3_abc123"));
        checkRejects("encoded separator at id end rejected", () -> RedditGalleryResponse.requestUrl("https://www.reddit.com/comments/abc123%2f"));
        checkRejects("encoded separator inside id rejected", () -> RedditGalleryResponse.requestUrl("https://www.reddit.com/r/x/comments/abc123%2Fdef/t"));
    }

    // 2. Ordered images, reversed metadata map, entity decoding, mp4 preference, preview fallback.
    private static void parseOrderedGallery() throws Exception {
        JSONObject post = new JSONObject()
            .put("gallery_data", new JSONObject()
                .put("items", new JSONArray()
                    .put(item("m2", "Café ☕", "https://example.com/out?a=1&amp;b=2"))
                    .put(item("m1", "", ""))))
            .put("media_metadata", new JSONObject()
                // Reversed relative to items order: m1 first, m2 second.
                .put("m1", new JSONObject()
                    .put("s", new JSONObject()
                        .put("u", "https://i.redd.it/m1.jpg")
                        .put("mp4", "https://v.redd.it/xyz/DASH_480.mp4")))
                .put("m2", metadata("https://i.redd.it/m2.jpg?w=1080&amp;format=pjpg", "https://i.redd.it/m2p.jpg")));
        Gallery gallery = RedditGalleryResponse.parse(threadOf(post));
        check("gallery parsed", gallery != null);
        if (gallery == null) {
            return;
        }
        check("items order wins over metadata map order",
            "https://i.redd.it/m2.jpg?w=1080&format=pjpg".equals(gallery.urls[0])
                && "https://v.redd.it/xyz/DASH_480.mp4".equals(gallery.urls[1]));
        check("mp4 source preferred over u when present", gallery.urls[1].endsWith("DASH_480.mp4"));
        check("preview from p array", "https://i.redd.it/m2p.jpg".equals(gallery.thumbnails[0]));
        check("preview falls back to media url", gallery.thumbnails[1].equals(gallery.urls[1]));
        check("utf-8 caption kept, outbound decoded and joined",
            ("Café ☕\n\nhttps://example.com/out?a=1&b=2").equals(gallery.captions[0]));
        check("empty caption and outbound stay empty", "".equals(gallery.captions[1]));
        check("arrays aligned", gallery.urls.length == 2 && gallery.thumbnails.length == 2 && gallery.captions.length == 2);
        check("default position zero", gallery.position == 0);
    }

    // 2b. Source candidates are decoded and validated one by one: invalid mp4 falls through to a
    // valid u, invalid mp4+u fall through to a valid gif, and decoding happens before validation.
    private static void sourceFallbackCases() throws Exception {
        JSONObject post = new JSONObject()
            .put("gallery_data", new JSONObject().put("items", new JSONArray()
                .put(item("a", "", ""))
                .put(item("b", "", ""))
                .put(item("c", "", ""))
                .put(item("d", "", ""))))
            .put("media_metadata", new JSONObject()
                .put("a", new JSONObject().put("s", new JSONObject()
                    .put("mp4", "http://invalid.example/a.mp4")
                    .put("u", "https://i.redd.it/a.jpg")))
                .put("b", new JSONObject().put("s", new JSONObject()
                    .put("mp4", "http://invalid.example/b.mp4")
                    .put("u", "http://invalid.example/b.jpg")
                    .put("gif", "https://i.redd.it/b.gif")))
                .put("c", new JSONObject().put("s", new JSONObject()
                    .put("u", "https://i.redd.it/c.jpg?a=1&amp;b=2")))
                .put("d", new JSONObject().put("s", new JSONObject()
                    // \u202e is stripped by entity unescaping: invalid raw, usable decoded.
                    .put("u", "https://i.redd.it/d\u202e.jpg"))));
        Gallery gallery = RedditGalleryResponse.parse(threadOf(post));
        check("invalid mp4 falls back to valid u", gallery != null && "https://i.redd.it/a.jpg".equals(gallery.urls[0]));
        check("invalid mp4 and u fall back to valid gif", gallery != null && "https://i.redd.it/b.gif".equals(gallery.urls[1]));
        check("decoded entity url returned, not the raw value", gallery != null && "https://i.redd.it/c.jpg?a=1&b=2".equals(gallery.urls[2]));
        check("decode runs before validation (stripped char)", gallery != null && "https://i.redd.it/d.jpg".equals(gallery.urls[3]));
    }

    // 2c. Preview fallback within p[] and separator-free caption variants.
    private static void previewAndCaptionCases() throws Exception {
        JSONObject post = new JSONObject()
            .put("gallery_data", new JSONObject().put("items", new JSONArray()
                .put(item("p", "", ""))
                .put(item("capOnly", "just a caption", ""))
                .put(item("outOnly", "", "https://example.com/only?a=1&amp;b=2"))))
            .put("media_metadata", new JSONObject()
                .put("p", new JSONObject()
                    .put("s", new JSONObject().put("u", "https://i.redd.it/p.jpg"))
                    .put("p", new JSONArray()
                        .put(new JSONObject().put("u", "http://invalid.example/p1.jpg"))
                        .put(new JSONObject().put("u", "https://i.redd.it/p2.jpg"))))
                .put("capOnly", metadata("https://i.redd.it/cap.jpg", null))
                .put("outOnly", metadata("https://i.redd.it/out.jpg", null)));
        Gallery gallery = RedditGalleryResponse.parse(threadOf(post));
        check("unusable first preview skips to usable second", gallery != null && "https://i.redd.it/p2.jpg".equals(gallery.thumbnails[0]));
        check("caption only has no separator", gallery != null && "just a caption".equals(gallery.captions[1]));
        check("outbound only is decoded without separator",
            gallery != null && "https://example.com/only?a=1&b=2".equals(gallery.captions[2]));
    }

    // 3. Skip unusable entries; all-unusable returns null.
    private static void parseSkips() throws Exception {
        JSONArray items = new JSONArray()
            .put(item("ok1", "", ""))
            .put(item("ghost", "", ""))           // no metadata entry
            .put(item("failed", "", ""))          // status=failed
            .put(item("badstatus", "", ""))       // present status of the wrong JSON type
            .put(item("nosrc", "", ""))           // no s object
            .put(item("insecure", "", ""))        // http source
            .put(item("userinfo", "", ""))        // userinfo in source url
            .put(item("ok2", "", ""));
        JSONObject metadata = new JSONObject()
            .put("ok1", metadata("https://i.redd.it/ok1.jpg", "https://i.redd.it/ok1p.jpg"))
            .put("failed", new JSONObject().put("status", "failed").put("s", new JSONObject().put("u", "https://i.redd.it/f.jpg")))
            .put("badstatus", new JSONObject().put("status", 42).put("s", new JSONObject().put("u", "https://i.redd.it/bs.jpg")))
            .put("nosrc", new JSONObject().put("p", new JSONArray().put(new JSONObject().put("u", "https://i.redd.it/x.jpg"))))
            .put("insecure", metadata("http://i.redd.it/bad.jpg", null))
            .put("userinfo", metadata("https://u:p@i.redd.it/bad.jpg", null))
            .put("ok2", metadata("https://i.redd.it/ok2.jpg", "https://i.redd.it/ok2p.jpg"));
        JSONObject post = new JSONObject()
            .put("gallery_data", new JSONObject().put("items", items))
            .put("media_metadata", metadata);
        Gallery gallery = RedditGalleryResponse.parse(threadOf(post));
        check("unusable entries skipped (incl. wrong-type status)", gallery != null && gallery.urls.length == 2);
        if (gallery != null) {
            check("compact arrays keep alignment",
                "https://i.redd.it/ok1.jpg".equals(gallery.urls[0]) && "https://i.redd.it/ok2.jpg".equals(gallery.urls[1])
                    && gallery.thumbnails.length == 2 && gallery.captions.length == 2
                    && "https://i.redd.it/ok1p.jpg".equals(gallery.thumbnails[0]));
        }

        JSONObject onlyBad = new JSONObject()
            .put("bad", metadata("http://i.redd.it/bad.jpg", null));
        JSONObject badPost = new JSONObject()
            .put("gallery_data", new JSONObject().put("items", new JSONArray().put(item("bad", "", ""))))
            .put("media_metadata", onlyBad);
        Gallery none = RedditGalleryResponse.parse(threadOf(badPost));
        check("all entries unusable returns null", none == null);
    }

    // 4. Empty/deleted/no-gallery become null.
    private static void parseEmptyCases() throws Exception {
        check("empty children returns null", RedditGalleryResponse.parse(emptyChildrenThread()) == null);
        check("no gallery metadata returns null",
            RedditGalleryResponse.parse(threadOf(new JSONObject().put("id", "abc123"))) == null);
    }

    private static void parseStructuralErrors() {
        checkThrows("malformed json", JSONException.class, () -> RedditGalleryResponse.parse("not json at all"));
        checkThrows("object instead of thread array", JSONException.class, () -> RedditGalleryResponse.parse("{}"));
        checkThrows("array of non-objects", JSONException.class, () -> RedditGalleryResponse.parse("[\"nope\"]"));
        checkThrows("listing without children", JSONException.class,
            () -> RedditGalleryResponse.parse("[{\"data\":{}}]"));
    }

    // 4b. Present non-null gallery fields of the wrong JSON type are structural errors, not
    // "no gallery"; absent/JSON-null fields still mean unavailable.
    private static void structuralTypeCases() throws Exception {
        JSONObject goodMeta = new JSONObject().put("m", metadata("https://i.redd.it/m.jpg", null));
        JSONArray oneItem = new JSONArray().put(item("m", "", ""));

        checkThrows("gallery_data array is structurally wrong", JSONException.class, () ->
            RedditGalleryResponse.parse(threadOf(new JSONObject()
                .put("gallery_data", new JSONArray()).put("media_metadata", goodMeta))));
        checkThrows("media_metadata string is structurally wrong", JSONException.class, () ->
            RedditGalleryResponse.parse(threadOf(new JSONObject()
                .put("gallery_data", new JSONObject().put("items", oneItem)).put("media_metadata", "bad"))));
        checkThrows("crosspost_parent_list string is structurally wrong", JSONException.class, () ->
            RedditGalleryResponse.parse(threadOf(new JSONObject().put("crosspost_parent_list", "x"))));
        checkThrows("items string is structurally wrong", JSONException.class, () ->
            RedditGalleryResponse.parse(threadOf(new JSONObject()
                .put("gallery_data", new JSONObject().put("items", "x")).put("media_metadata", goodMeta))));
        checkThrows("non-object item member is structurally wrong", JSONException.class, () ->
            RedditGalleryResponse.parse(threadOf(new JSONObject()
                .put("gallery_data", new JSONObject().put("items", new JSONArray().put(123)))
                .put("media_metadata", goodMeta))));

        check("JSON-null gallery_data means unavailable, not an error",
            RedditGalleryResponse.parse(threadOf(new JSONObject()
                .put("gallery_data", JSONObject.NULL).put("media_metadata", goodMeta))) == null);
        check("JSON-null media_metadata means unavailable, not an error",
            RedditGalleryResponse.parse(threadOf(new JSONObject()
                .put("gallery_data", new JSONObject().put("items", oneItem)).put("media_metadata", JSONObject.NULL))) == null);
        check("JSON-null crosspost_parent_list means unavailable, not an error",
            RedditGalleryResponse.parse(threadOf(new JSONObject().put("crosspost_parent_list", JSONObject.NULL))) == null);
    }

    // 5. Crosspost parent metadata fallback.
    private static void crosspostFallback() throws Exception {
        JSONObject parent = new JSONObject()
            .put("gallery_data", new JSONObject().put("items", new JSONArray().put(item("p1", "", ""))))
            .put("media_metadata", new JSONObject().put("p1", metadata("https://i.redd.it/p1.jpg", "https://i.redd.it/p1p.jpg")));
        JSONObject crossposter = new JSONObject()
            .put("id", "abc123")
            .put("crosspost_parent_list", new JSONArray()
                .put(new JSONObject().put("id", "noGal"))
                .put(parent));
        Gallery gallery = RedditGalleryResponse.parse(threadOf(crossposter));
        check("crosspost parent gallery metadata used", gallery != null && gallery.urls.length == 1);
        if (gallery != null) {
            check("crosspost media url read from parent", "https://i.redd.it/p1.jpg".equals(gallery.urls[0]));
        }
    }

    // 6. Position default and clamps, including the grid page value.
    private static void positionCases() throws Exception {
        JSONObject post = new JSONObject()
            .put("gallery_data", new JSONObject()
                .put("items", new JSONArray().put(item("a", "", "")).put(item("b", "", ""))))
            .put("media_metadata", new JSONObject()
                .put("a", metadata("https://i.redd.it/a.jpg", null))
                .put("b", metadata("https://i.redd.it/b.jpg", null)));

        Gallery negative = RedditGalleryResponse.parse(threadOf(post, -5));
        check("negative position clamps to zero", negative != null && negative.position == 0);

        Gallery oversized = RedditGalleryResponse.parse(threadOf(post, 99));
        check("oversized position clamps to image count (grid page)", oversized != null && oversized.position == 2);

        Gallery exact = RedditGalleryResponse.parse(threadOf(post, 2));
        check("grid page position preserved", exact != null && exact.position == 2);

        JSONObject singlePost = new JSONObject()
            .put("gallery_data", new JSONObject().put("items", new JSONArray().put(item("s1", "", ""))))
            .put("media_metadata", new JSONObject().put("s1", metadata("https://i.redd.it/s1.jpg", null)));
        Gallery oneAtPageOne = RedditGalleryResponse.parse(threadOf(singlePost, 1));
        check("one-image gallery has no page 1", oneAtPageOne != null && oneAtPageOne.position == 0);
        Gallery oneOversized = RedditGalleryResponse.parse(threadOf(singlePost, 99));
        check("one-image oversized position clamps to 0", oneOversized != null && oneOversized.position == 0);
    }

    // 7. withPosition round-trip keeps payload parseable; position 2 is a real grid page here.
    private static void withPositionRoundTrip() throws Exception {
        JSONObject post = new JSONObject()
            .put("gallery_data", new JSONObject().put("items", new JSONArray()
                .put(item("m1", "cap", ""))
                .put(item("m2", "", ""))))
            .put("media_metadata", new JSONObject()
                .put("m1", metadata("https://i.redd.it/m1.jpg", null))
                .put("m2", metadata("https://i.redd.it/m2.jpg", null)));
        String original = threadOf(post);

        String positioned = RedditGalleryResponse.withPosition(original, 2);
        JSONArray root = new JSONArray(positioned);
        check("withPosition returns a parseable array", root.length() == 2);
        check("position stored on first listing", root.optJSONObject(0) != null && root.optJSONObject(0).optInt("position", -1) == 2);
        Gallery gallery = RedditGalleryResponse.parse(positioned);
        check("gallery payload survives round trip",
            gallery != null && gallery.urls.length == 2 && "cap".equals(gallery.captions[0])
                && "https://i.redd.it/m2.jpg".equals(gallery.urls[1]));
        check("position restored through parse", gallery != null && gallery.position == 2);
        JSONObject commentsAfter = root.optJSONObject(1);
        check("comment payload preserved", commentsAfter != null
            && "COMMENT_BODY".equals(commentsAfter.optJSONObject("data").optJSONArray("children").optJSONObject(0).optJSONObject("data").optString("body", "")));

        String clamped = RedditGalleryResponse.withPosition(original, -3);
        check("withPosition clamps negative to zero", new JSONArray(clamped).optJSONObject(0).optInt("position", -1) == 0);
    }

    // 8. Token JSON: normal, missing, blank, wrong type.
    private static void tokenJsonCases() {
        try {
            check("normal token read", "synthetic-token-ABC123".equals(RedgifsResponse.token("{\"token\":\"synthetic-token-ABC123\"}")));
        } catch (JSONException e) {
            check("normal token read", false);
        }
        checkThrows("missing token", JSONException.class, () -> RedgifsResponse.token("{}"));
        checkThrows("wrong-type token (number)", JSONException.class, () -> RedgifsResponse.token("{\"token\":42}"));
        checkThrows("wrong-type token (object)", JSONException.class, () -> RedgifsResponse.token("{\"token\":{}}"));
        checkThrows("malformed token body", JSONException.class, () -> RedgifsResponse.token("<html>"));
        checkThrows("blank token", IllegalArgumentException.class, () -> RedgifsResponse.token("{\"token\":\" \"}"));
        checkThrows("whitespace token", IllegalArgumentException.class, () -> RedgifsResponse.token("{\"token\":\"bad token\"}"));
        checkThrows("CRLF token", IllegalArgumentException.class, () -> RedgifsResponse.token("{\"token\":\"bad\\ntoken\"}"));
    }

    // 9. Media JSON: hd/sd preference and fallback, HTML-only and malformed rejected.
    private static void mediaJsonCases() {
        String both = "{\"gif\":{\"urls\":{"
            + "\"hd\":\"https://media.redgifs.com/Synthetic-silent.mp4?sig=1\","
            + "\"sd\":\"https://files.redgifs.com/Synthetic-mobile.mp4\"}}}";
        try {
            check("hd preferred", "https://media.redgifs.com/Synthetic-silent.mp4?sig=1".equals(RedgifsResponse.media(both, true)));
            check("sd preferred when asked", "https://files.redgifs.com/Synthetic-mobile.mp4".equals(RedgifsResponse.media(both, false)));
            check("missing sd falls back to hd",
                "https://media.redgifs.com/Only.mp4".equals(RedgifsResponse.media("{\"gif\":{\"urls\":{\"hd\":\"https://media.redgifs.com/Only.mp4\"}}}", false)));
            check("missing hd falls back to sd",
                "https://media.redgifs.com/Only.mp4".equals(RedgifsResponse.media("{\"gif\":{\"urls\":{\"sd\":\"https://media.redgifs.com/Only.mp4\"}}}", true)));
        } catch (JSONException e) {
            check("media preference cases", false);
        }
        checkThrows("html-only response rejected", IllegalArgumentException.class,
            () -> RedgifsResponse.media("{\"gif\":{\"urls\":{\"html\":\"https://www.redgifs.com/ifr/x\"}}}", true));
        checkThrows("malformed media body", JSONException.class, () -> RedgifsResponse.media("not json", true));
        checkThrows("missing gif rejected", JSONException.class, () -> RedgifsResponse.media("{\"urls\":{}}", true));
        checkThrows("gif not an object rejected", JSONException.class, () -> RedgifsResponse.media("{\"gif\":\"x\"}", true));
        checkThrows("missing urls rejected", JSONException.class, () -> RedgifsResponse.media("{\"gif\":{}}", true));
        checkThrows("non-usable hd and sd rejected", IllegalArgumentException.class,
            () -> RedgifsResponse.media("{\"gif\":{\"urls\":{\"hd\":\"http://media.redgifs.com/a.mp4\",\"sd\":\"https://media.redgifs.com/b.webm\"}}}", true));
    }

    // ---- fixture builders (synthetic) ----

    /** Thread array [postListing, commentsListing]; position optionally set on the first listing. */
    private static String threadOf(JSONObject postData) throws Exception {
        return threadOf(postData, null);
    }

    private static String threadOf(JSONObject postData, Integer position) throws Exception {
        JSONObject firstListing = new JSONObject()
            .put("data", new JSONObject()
                .put("children", new JSONArray().put(new JSONObject().put("data", postData))));
        if (position != null) {
            firstListing.put("position", position);
        }
        JSONObject commentsListing = new JSONObject()
            .put("data", new JSONObject()
                .put("children", new JSONArray()
                    .put(new JSONObject().put("data", new JSONObject().put("body", "COMMENT_BODY")))));
        return new JSONArray().put(firstListing).put(commentsListing).toString();
    }

    private static String emptyChildrenThread() throws Exception {
        return new JSONArray()
            .put(new JSONObject().put("data", new JSONObject().put("children", new JSONArray())))
            .put(new JSONObject().put("data", new JSONObject().put("children", new JSONArray())))
            .toString();
    }

    private static JSONObject item(String mediaId, String caption, String outbound) throws JSONException {
        JSONObject item = new JSONObject().put("media_id", mediaId);
        if (!"".equals(caption)) {
            item.put("caption", caption);
        }
        if (!"".equals(outbound)) {
            item.put("outbound_url", outbound);
        }
        return item;
    }

    private static JSONObject metadata(String sourceUrl, String previewUrl) throws JSONException {
        JSONObject meta = new JSONObject().put("s", new JSONObject().put("u", sourceUrl));
        if (previewUrl != null) {
            meta.put("p", new JSONArray().put(new JSONObject().put("u", previewUrl)));
        }
        return meta;
    }

    // ---- assertion helpers ----

    private static void check(String name, boolean condition) {
        if (condition) {
            passes++;
            System.out.println("pass: " + name);
        } else {
            failures++;
            System.out.println("FAIL: " + name);
        }
    }

    private static void checkRejects(String name, Call call) {
        try {
            call.run();
        } catch (IllegalArgumentException expected) {
            check(name, true);
            return;
        } catch (Exception other) {
            check(name + " (unexpected " + other.getClass().getSimpleName() + ")", false);
            return;
        }
        check(name + " (no IllegalArgumentException thrown)", false);
    }

    private static void checkThrows(String name, Class<? extends Exception> type, Call call) {
        try {
            call.run();
        } catch (Exception e) {
            check(name, type.isInstance(e));
            return;
        }
        check(name + " (nothing thrown)", false);
    }
}
