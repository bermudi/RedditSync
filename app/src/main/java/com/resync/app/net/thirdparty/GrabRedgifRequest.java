package com.resync.app.net.thirdparty;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import com.resync.app.net.LegacyCustomHttpHeaderParser;
import com.resync.app.util.Logger;
import com.resync.app.util.links.LinkHandler;

/**
 * Resolves a RedGifs link to a playable MP4 URL.
 *
 * <p>RedGifs' public web endpoints kept moving (the {@code /ifr/<id>} embed page this used to
 * scrape now answers 404), but their v2 JSON API is stable and open: a temporary token from
 * {@code /v2/auth/temporary} unlocks {@code /v2/gifs/{id}}, which lists the direct media files
 * under {@code urls.hd} / {@code urls.sd}. Tokens last about a day and are tied to the user agent
 * they were issued to, so the same user agent is sent with both calls, and a rejected token is
 * dropped so the next attempt mints a fresh one.
 */
public class GrabRedgifRequest extends Request<String> {

    private static final String TEMPORARY_TOKEN_URL = "https://api.redgifs.com/v2/auth/temporary";
    /** Redgifs tokens last a day; refresh an hour early. */
    private static final long TOKEN_LIFETIME_MILLIS = 23L * 60L * 60L * 1000L;

    // RedGifs rejects a request whose user agent differs from the one the token was issued to,
    // so one agent is used everywhere here.
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final Object TOKEN_LOCK = new Object();
    private static String cachedToken;
    private static long cachedTokenExpiresAt;

    private final RedgifRequestWrapper wrapper;

    public GrabRedgifRequest(RedgifRequestWrapper redgifRequestWrapper) {
        super(0, buildUrl(redgifRequestWrapper), redgifRequestWrapper.getErrorListener());
        this.wrapper = redgifRequestWrapper;
        setShouldCache(true);
    }

    static final String buildUrl(RedgifRequestWrapper redgifRequestWrapper) {
        return "https://api.redgifs.com/v2/gifs/" + LinkHandler.getGfycatId(redgifRequestWrapper.getGifUrl());
    }

    @Override // com.android.volley.Request
    public Map<String, String> getHeaders() throws AuthFailureError {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json");
        // Runs on a Volley network thread, so minting here only blocks that thread.
        String token = token();
        if (token != null) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    @Override // com.android.volley.Request
    public Response<String> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            JSONObject urls = new JSONObject(new String(networkResponse.data)).getJSONObject("gif").getJSONObject("urls");
            // forceHd wants the full-quality file; otherwise the smaller mobile file plays sooner.
            String url = this.wrapper.isForceHd()
                    ? firstNonEmpty(urls, "hd", "sd")
                    : firstNonEmpty(urls, "sd", "hd");
            if (url != null) {
                return Response.success(url, LegacyCustomHttpHeaderParser.parseCacheEntry(networkResponse));
            }
            return Response.error(new VolleyError("No media URL in RedGifs response"));
        } catch (Exception e) {
            Logger.logException(e);
            return Response.error(new VolleyError("Error parsing RedGifs response"));
        }
    }

    @Override // com.android.volley.Request
    protected VolleyError parseNetworkError(VolleyError volleyError) {
        // A rejected (expired) token is the likely cause; drop it so the next attempt re-mints.
        synchronized (TOKEN_LOCK) {
            cachedToken = null;
            cachedTokenExpiresAt = 0;
        }
        return volleyError;
    }

    @Override // com.android.volley.Request
    public void deliverResponse(String str) {
        this.wrapper.deliverResponse(str);
    }

    private static String firstNonEmpty(JSONObject urls, String first, String second) {
        String value = urls.optString(first, "");
        if (value.isEmpty()) {
            value = urls.optString(second, "");
        }
        return value.isEmpty() ? null : value;
    }

    private static String token() {
        synchronized (TOKEN_LOCK) {
            if (cachedToken != null && cachedTokenExpiresAt > System.currentTimeMillis()) {
                return cachedToken;
            }
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(TEMPORARY_TOKEN_URL).openConnection();
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/json");
                connection.setUseCaches(false);
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Logger.verbose("RedGifs token request returned " + connection.getResponseCode());
                    return null;
                }
                String token = new JSONObject(read(connection.getInputStream())).getString("token");
                cachedToken = token;
                cachedTokenExpiresAt = System.currentTimeMillis() + TOKEN_LIFETIME_MILLIS;
                return token;
            } catch (Exception e) {
                Logger.logException(e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
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
