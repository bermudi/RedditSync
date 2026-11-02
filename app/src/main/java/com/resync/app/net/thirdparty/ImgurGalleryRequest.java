package com.resync.app.net.thirdparty;

import android.text.Html;
import android.text.TextUtils;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import com.resync.app.media.imgur.ImgurImageHelper;
import com.resync.app.model.media.Image;
import com.resync.app.model.Gallery;
import com.resync.app.util.GalleryPositions;
import com.resync.app.util.Logger;

/* JADX INFO: renamed from: s8.b */
/* JADX INFO: compiled from: ImgurGalleryRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class ImgurGalleryRequest extends Request<Gallery> {

    private static final String ALBUM_URL = "https://imgur.com/a/";

    /**
     * The page Imgur serves for embedding an album elsewhere. The album page itself only names
     * its cover, while this one still carries the whole list written into it, and it needs no
     * credential to ask for.
     */
    private static final String EMBED_URL_SUFFIX = "/embed?pub=true";

    /** What the embed page calls the list. */
    private static final String EMBEDDED_IMAGES = "var images";

    /**
     * What a live album page carries: a run of JavaScript assigning a string, and that string is
     * the album written as JSON.
     */
    private static final Pattern EMBEDDED_POST = Pattern.compile(
            "postDataJSON\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final String albumId;
    private final Response.Listener<Gallery> responseListener;

    public static class AlbumNotFoundError extends VolleyError {
    }

    public ImgurGalleryRequest(String str, Response.Listener<Gallery> listener, Response.ErrorListener errorListener) {
        // No Imgur API key: the API needs a Client-ID this build doesn't have, so the album is
        // read from the album page itself ( postDataJSON) with the embed page as fallback — both
        // keyless. Ported from sync-up's keyless Imgur recovery.
        super(0, buildUrl(str), errorListener);
        this.albumId = ImgurImageHelper.getId(ImgurImageHelper.normalizeUrl(str));
        this.responseListener = listener;
        setShouldCache(true);
        Logger.verbose(getUrl());
    }

    public static String buildUrl(String str) {
        return ALBUM_URL + ImgurImageHelper.getId(ImgurImageHelper.normalizeUrl(str));
    }

    @Override // com.android.volley.Request
    public void deliverResponse(Gallery gallery) {
        this.responseListener.onResponse(gallery);
    }

    @Override // com.android.volley.Request
    public Response<Gallery> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            String page = new String(networkResponse.data);
            List<JSONObject> images = imagesInPostData(page);
            if (images.isEmpty()) {
                // The album page is a script shell that names nothing for recent layouts; the
                // embed of it still answers. Runs on a Volley network thread, like the album page.
                images = imagesInEmbed(this.albumId);
            }
            if (images.isEmpty()) {
                return Response.error(new AlbumNotFoundError());
            }

            Gallery gallery = new Gallery(images.size());
            for (int index = 0; index < images.size(); index++) {
                JSONObject entry = images.get(index);
                Image image = new Image(entry.getString("link"));
                gallery.urls[index] = image.getUrl();
                gallery.thumbnails[index] = image.getBigThumbnailUrl();
                String title = literalOrNull(entry, "title");
                String description = literalOrNull(entry, "description");
                StringBuffer stringBuffer = new StringBuffer();
                if (!TextUtils.isEmpty(title)) {
                    stringBuffer.append(title);
                }
                if (!TextUtils.isEmpty(stringBuffer) && !TextUtils.isEmpty(description)) {
                    stringBuffer.append(" - ");
                }
                if (!TextUtils.isEmpty(description)) {
                    stringBuffer.append(description);
                }
                if (!TextUtils.isEmpty(stringBuffer)) {
                    gallery.captions[index] = Html.fromHtml(stringBuffer.toString()).toString();
                }
            }
            gallery.position = GalleryPositions.get(getUrl());
            return Response.success(gallery, HttpHeaderParser.parseCacheHeaders(networkResponse));
        } catch (Exception e) {
            Logger.logException(e);
            return Response.error(new ParseError(e));
        }
    }

    /** The album as its own page gives it: media entries with url (or id+ext) and dimensions. */
    private static List<JSONObject> imagesInPostData(String page) throws Exception {
        List<JSONObject> images = new ArrayList<>();
        Matcher found = EMBEDDED_POST.matcher(page);
        if (!found.find()) {
            return images;
        }
        // The album is written as JSON inside a JavaScript string; read the string first.
        String written = new JSONArray("[\"" + found.group(1) + "\"]").getString(0);
        JSONArray media = new JSONObject(written).optJSONArray("media");
        if (media == null) {
            return images;
        }
        for (int i = 0; i < media.length(); i++) {
            JSONObject entry = media.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String link = entry.optString("url", "");
            if (link.isEmpty()) {
                String id = entry.optString("id", "");
                if (id.isEmpty()) {
                    continue;
                }
                link = "https://i.imgur.com/" + id + "." + entry.optString("ext", "jpg");
            }
            images.add(image(link, entry.optString("name"), entry.optString("description")));
        }
        return images;
    }

    /** The album as the page for embedding it gives it: hash, extension, dimensions per image. */
    private static List<JSONObject> imagesInEmbed(String albumId) throws Exception {
        List<JSONObject> images = new ArrayList<>();
        if (TextUtils.isEmpty(albumId)) {
            return images;
        }
        HttpURLConnection connection = null;
        String html;
        try {
            connection = (HttpURLConnection) new URL(ALBUM_URL + albumId + EMBED_URL_SUFFIX).openConnection();
            connection.setUseCaches(false);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return images;
            }
            html = read(connection.getInputStream());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        int at = html.indexOf(EMBEDDED_IMAGES);
        if (at < 0) {
            return images;
        }
        int start = html.indexOf('{', at + EMBEDDED_IMAGES.length());
        if (start < 0) {
            return images;
        }
        String block = objectAt(html, start);
        if (block == null) {
            return images;
        }
        JSONArray entries = new JSONObject(block).optJSONArray("images");
        if (entries == null) {
            return images;
        }
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String hash = entry.optString("hash", "");
            if (hash.isEmpty()) {
                continue;
            }
            images.add(image(
                    "https://i.imgur.com/" + hash + entry.optString("ext", ".jpg"),
                    entry.optString("title"),
                    entry.optString("description")));
        }
        return images;
    }

    private static JSONObject image(String link, String title, String description) throws Exception {
        JSONObject image = new JSONObject();
        image.put("link", link);
        image.put("title", title == null ? "" : title);
        image.put("description", description == null ? "" : description);
        return image;
    }

    /** The imgur API wrote the literal string "null" for absent text; keep treating it as absent. */
    private static String literalOrNull(JSONObject entry, String field) {
        String value = entry.optString(field, "");
        return "null".equals(value) ? "" : value;
    }

    /** Reads one JSON object out of a larger document, respecting braces inside strings. */
    private static String objectAt(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            for (int n; (n = in.read(buffer)) >= 0; ) {
                out.write(buffer, 0, n);
            }
        } finally {
            in.close();
        }
        return out.toString("UTF-8");
    }
}
