package com.resync.app.net.thirdparty;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.resync.app.RedditApplication;
import com.resync.app.helpers.ConnectivityHelper;
import com.resync.app.net.LegacyCustomHttpHeaderParser;
import com.resync.app.util.Logger;
import com.resync.app.util.links.LinkHandler;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Resolves a RedGifs (or Gfycat, whose content moved to RedGifs under the same slug) link to a
 * direct MP4 URL through RedGifs' v2 API: a temporary token from {@link RedgifsTokens} unlocks
 * {@code /v2/gifs/{id}}, whose {@code urls.hd}/{@code urls.sd} are the media files.
 *
 * <p>A rejected (expired) token is the one failure worth acting on: Volley's own retry would
 * resend the same stale token, so on a 401/403 the token is dropped and a fresh request — which
 * mints a new token in its headers — takes over, and this request's error is swallowed.
 */
public abstract class RedgifsGifRequest extends Request<String> {

    private final boolean forceFullQuality;
    private final boolean isRetry;
    private volatile boolean retryEnqueued;

    protected RedgifsGifRequest(String gifUrl, boolean forceFullQuality, boolean isRetry, Response.ErrorListener errorListener) {
        super(0, "https://api.redgifs.com/v2/gifs/" + LinkHandler.getGfycatId(gifUrl), errorListener);
        this.forceFullQuality = forceFullQuality;
        this.isRetry = isRetry;
        setShouldCache(true);
    }

    /** The same gif asked for again, with a fresh token. */
    protected abstract Request<String> retryClone();

    @Override // com.android.volley.Request
    public Map<String, String> getHeaders() throws AuthFailureError {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", RedgifsTokens.USER_AGENT);
        headers.put("Accept", "application/json");
        // Runs on a Volley network thread, so minting here only blocks that thread.
        String token = RedgifsTokens.token();
        if (token != null) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    @Override // com.android.volley.Request
    public Response<String> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            JSONObject urls = new JSONObject(new String(networkResponse.data)).getJSONObject("gif").getJSONObject("urls");
            // Full quality when asked for, or free enough on WiFi; otherwise the smaller mobile
            // file starts playing sooner.
            boolean preferHd = this.forceFullQuality || ConnectivityHelper.isWifiConnected();
            String url = preferHd ? firstNonEmpty(urls, "hd", "sd") : firstNonEmpty(urls, "sd", "hd");
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
        NetworkResponse response = volleyError.networkResponse;
        if (!this.isRetry && response != null
                && (response.statusCode == 401 || response.statusCode == 403)) {
            RedgifsTokens.clear();
            this.retryEnqueued = true;
            RedditApplication.thirdPartyRequestQueue.add(retryClone());
        }
        return volleyError;
    }

    @Override // com.android.volley.Request
    public void deliverError(VolleyError volleyError) {
        // A retry is in flight with a fresh token; it answers for this one.
        if (this.retryEnqueued) {
            return;
        }
        super.deliverError(volleyError);
    }

    private static String firstNonEmpty(JSONObject urls, String first, String second) {
        String value = urls.optString(first, "");
        if (value.isEmpty()) {
            value = urls.optString(second, "");
        }
        return value.isEmpty() ? null : value;
    }
}
