package com.resync.app.net.oauth;

import com.resync.app.model.Gallery;
import com.resync.app.util.WebsafeUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reddit gallery boundary helpers: canonical thread-endpoint request URL, thread-response parsing
 * into the Gallery model, and cache-position rewriting.
 *
 * <p>Plain Java plus org.json, the Gallery model and WebsafeUtils only, so the gallery contract
 * stays testable offline. The thread response is a top-level JSON array; the post with its
 * {@code gallery_data}/{@code media_metadata} lives in the first listing's
 * {@code data.children[0].data}. A {@code position} int may sit on the first listing object; the
 * viewer writes it back to remember the last viewed page. Route and id validation uses the raw
 * (still percent-encoded) path so encoded delimiters cannot rewrite routing.
 */
public final class RedditGalleryResponse {

    private RedditGalleryResponse() {
    }

    /**
     * Validates an HTTPS reddit.com gallery/comments permalink and returns the oauth
     * thread-endpoint URL that carries the post listing. Accepts root/www/old/new hosts
     * (explicit port 443 tolerated), {@code /r/<sub>/comments/<id>/...}, {@code /comments/<id>/...}
     * or {@code /gallery/<id>} paths; query and fragment are ignored, trailing title segments are
     * ignored, and the id segment (raw, before any percent-decoding) must be base36 and is
     * canonicalized to lowercase.
     *
     * @throws IllegalArgumentException on a non-Reddit host, user-info, a non-default port, or a
     *                                  missing/malformed post id.
     */
    public static String requestUrl(String galleryUrl) {
        URI uri = parseUri(galleryUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Not an HTTPS reddit URL");
        }
        if (uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("Unexpected user-info or port");
        }
        String host = uri.getHost();
        if (host == null || !isRedditHost(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Not a reddit.com host");
        }
        String id = extractPostId(uri.getRawPath());
        return "https://oauth.reddit.com/comments/" + id + ".json?raw_json=1&limit=0&depth=1";
    }

    /**
     * Parses a thread-endpoint response into a Gallery, or returns null when the response carries
     * no usable gallery (empty children, deleted post, gallery metadata absent/null everywhere).
     * Iteration follows {@code gallery_data.items} order; metadata entries that are missing or
     * marked failed are skipped, as are entries with no usable HTTPS media.
     *
     * @throws JSONException on malformed JSON or structurally wrong containers: a non-array
     *                       thread body, a listing without children, or present non-null
     *                       gallery_data/media_metadata/crosspost_parent_list/items entries of
     *                       the wrong JSON type.
     */
    public static Gallery parse(String response) throws JSONException {
        JSONArray root = new JSONArray(response);
        if (root.length() == 0) {
            throw new JSONException("Empty thread listing array");
        }
        JSONObject listing = requireObject(root, 0);
        JSONObject listingData = requireObject(listing, "data");
        JSONArray children = requireArray(listingData, "children");
        if (children.length() == 0) {
            return null;
        }
        JSONObject postData = requireObject(requireObject(children, 0), "data");

        JSONObject galleryData = optionalObject(postData, "gallery_data");
        JSONObject metadata = optionalObject(postData, "media_metadata");
        if (galleryData == null || metadata == null) {
            JSONObject parent = firstCrosspostWithGallery(postData);
            if (parent != null) {
                galleryData = optionalObject(parent, "gallery_data");
                metadata = optionalObject(parent, "media_metadata");
            }
        }
        if (galleryData == null || metadata == null) {
            return null;
        }
        JSONArray items = requireArray(galleryData, "items");

        List<GalleryItem> collected = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = requireObject(items, i);
            GalleryItem parsedItem = parseItem(item, metadata);
            if (parsedItem != null) {
                collected.add(parsedItem);
            }
        }
        if (collected.isEmpty()) {
            return null;
        }

        Gallery gallery = new Gallery(collected.size());
        for (int i = 0; i < collected.size(); i++) {
            GalleryItem galleryItem = collected.get(i);
            gallery.urls[i] = galleryItem.url;
            gallery.thumbnails[i] = galleryItem.previewUrl;
            gallery.captions[i] = galleryItem.caption;
        }
        gallery.position = clampPosition(listing.optInt("position", 0), collected.size());
        return gallery;
    }

    /**
     * Writes a nonnegative {@code position} onto the first listing object of a cached thread
     * response, preserving the array shape and all listing/post/comment payload.
     */
    public static String withPosition(String response, int position) throws JSONException {
        JSONArray root = new JSONArray(response);
        if (root.length() == 0) {
            throw new JSONException("Empty thread listing array");
        }
        requireObject(root, 0).put("position", Math.max(0, position));
        return root.toString();
    }

    private static GalleryItem parseItem(JSONObject item, JSONObject metadata) throws JSONException {
        Object mediaId = item.opt("media_id");
        if (!(mediaId instanceof String)) {
            return null;
        }
        JSONObject meta = metadata.optJSONObject((String) mediaId);
        if (meta == null) {
            return null;
        }
        Object status = meta.opt("status");
        if (status != null && status != JSONObject.NULL
            && (!(status instanceof String) || !"valid".equalsIgnoreCase((String) status))) {
            return null;
        }
        JSONObject source = meta.optJSONObject("s");
        if (source == null) {
            return null;
        }
        String url = firstUsableSource(source);
        if (url == null) {
            return null;
        }
        String previewUrl = firstUsablePreview(meta);
        if (previewUrl == null) {
            previewUrl = url;
        }
        return new GalleryItem(url, previewUrl, buildCaption(item));
    }

    /** Tries mp4, then u, then gif; each candidate is entity-decoded and must be a usable HTTPS URL. */
    private static String firstUsableSource(JSONObject source) {
        String[] keys = {"mp4", "u", "gif"};
        for (String key : keys) {
            Object value = source.opt(key);
            if (!(value instanceof String) || ((String) value).isEmpty()) {
                continue;
            }
            String candidate = WebsafeUtils.unescapeHtml((String) value);
            if (isUsableMediaUrl(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String firstUsablePreview(JSONObject meta) {
        JSONArray previews = meta.optJSONArray("p");
        if (previews == null) {
            return null;
        }
        for (int i = 0; i < previews.length(); i++) {
            JSONObject preview = previews.optJSONObject(i);
            if (preview == null) {
                continue;
            }
            Object raw = preview.opt("u");
            if (!(raw instanceof String)) {
                continue;
            }
            String candidate = WebsafeUtils.unescapeHtml((String) raw);
            if (isUsableMediaUrl(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Nonempty caption and outbound_url joined with exactly two newlines; no trailing separator. */
    private static String buildCaption(JSONObject item) {
        Object captionRaw = item.opt("caption");
        Object outboundRaw = item.opt("outbound_url");
        String caption = captionRaw instanceof String ? WebsafeUtils.unescapeHtml((String) captionRaw).trim() : "";
        String outbound = outboundRaw instanceof String ? WebsafeUtils.unescapeHtml((String) outboundRaw).trim() : "";
        if (caption.isEmpty()) {
            return outbound;
        }
        if (outbound.isEmpty()) {
            return caption;
        }
        return caption + "\n\n" + outbound;
    }

    private static JSONObject firstCrosspostWithGallery(JSONObject postData) throws JSONException {
        JSONArray crossposts = optionalArray(postData, "crosspost_parent_list");
        if (crossposts == null) {
            return null;
        }
        for (int i = 0; i < crossposts.length(); i++) {
            JSONObject parent = crossposts.optJSONObject(i);
            if (parent != null && optionalObject(parent, "gallery_data") != null
                && optionalObject(parent, "media_metadata") != null) {
                return parent;
            }
        }
        return null;
    }

    /** HTTPS with a host, no user-info, default or 443 port. */
    private static boolean isUsableMediaUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        int port = uri.getPort();
        return port == -1 || port == 443;
    }

    private static int clampPosition(int position, int imageCount) {
        if (position < 0) {
            return 0;
        }
        // The pager's last page is the grid page, which exists only for two or more images
        // (ImagePagerAdapter.getCount). A single-image gallery has no page beyond index 0.
        int lastPage = imageCount > 1 ? imageCount : 0;
        return Math.min(position, lastPage);
    }

    private static boolean isRedditHost(String host) {
        return "reddit.com".equals(host) || "www.reddit.com".equals(host)
            || "old.reddit.com".equals(host) || "new.reddit.com".equals(host);
    }

    private static String extractPostId(String rawPath) {
        // Trailing title segments are routing-neutral, so empty trailing segments are tolerated.
        String[] segments = rawPath == null ? new String[0] : rawPath.split("/");
        int idIndex = -1;
        if (segments.length >= 5 && "r".equals(segments[1]) && "comments".equals(segments[3])) {
            idIndex = 4;
        } else if (segments.length >= 3 && ("comments".equals(segments[1]) || "gallery".equals(segments[1]))) {
            idIndex = 2;
        }
        if (idIndex == -1) {
            throw new IllegalArgumentException("Not a gallery/comments path");
        }
        String id = segments[idIndex];
        if (id.endsWith(".json")) {
            id = id.substring(0, id.length() - ".json".length());
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Missing post id");
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean base36 = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (!base36) {
                throw new IllegalArgumentException("Malformed post id");
            }
        }
        return id.toLowerCase(Locale.ROOT);
    }

    private static URI parseUri(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Null gallery URL");
        }
        try {
            return new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed gallery URL");
        }
    }

    /** Null when absent or JSON null; a present non-null value of another type is a structural error. */
    private static JSONObject optionalObject(JSONObject object, String key) throws JSONException {
        Object raw = object.opt(key);
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (!(raw instanceof JSONObject)) {
            throw new JSONException("Wrongly typed gallery field: " + key);
        }
        return (JSONObject) raw;
    }

    private static JSONArray optionalArray(JSONObject object, String key) throws JSONException {
        Object raw = object.opt(key);
        if (raw == null || raw == JSONObject.NULL) {
            return null;
        }
        if (!(raw instanceof JSONArray)) {
            throw new JSONException("Wrongly typed gallery field: " + key);
        }
        return (JSONArray) raw;
    }

    private static JSONObject requireObject(JSONArray array, int index) throws JSONException {
        JSONObject object = array.optJSONObject(index);
        if (object == null) {
            throw new JSONException("Expected JSON object at array index " + index);
        }
        return object;
    }

    private static JSONObject requireObject(JSONObject object, String key) throws JSONException {
        JSONObject child = object.optJSONObject(key);
        if (child == null) {
            throw new JSONException("Expected JSON object at key " + key);
        }
        return child;
    }

    private static JSONArray requireArray(JSONObject object, String key) throws JSONException {
        JSONArray array = object.optJSONArray(key);
        if (array == null) {
            throw new JSONException("Expected JSON array at key " + key);
        }
        return array;
    }

    private static final class GalleryItem {
        final String url;
        final String previewUrl;
        final String caption;

        GalleryItem(String url, String previewUrl, String caption) {
            this.url = url;
            this.previewUrl = previewUrl;
            this.caption = caption;
        }
    }
}
