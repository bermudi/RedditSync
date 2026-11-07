package com.resync.app.net.oauth;

import android.util.Log;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.resync.app.RedditApplication;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.time.DateUtils;
import org.json.JSONException;
import com.resync.app.model.Gallery;
import com.resync.app.net.CustomHttpHeaderParser;
import com.resync.app.net.RedditRequest;

/**
 * Loads a Reddit gallery post from the oauth thread endpoint. URL building, parsing and cache
 * position handling live in {@link RedditGalleryResponse}; this class only carries the request
 * through the bearer-authenticated queue and logs fixed phase/status messages.
 */
public class OAuthGalleryRequest extends RedditRequest<Gallery> {

    private static final String LOG_TAG = "RedditGallery";

    public OAuthGalleryRequest(String str, Response.Listener<Gallery> listener, Response.ErrorListener errorListener) {
        super(RedditApplication.getAppContext(), 0, buildUrl(str), listener, errorListener);
        setShouldCache(true);
    }

    public static String buildUrl(String str) {
        return RedditGalleryResponse.requestUrl(str);
    }

    @Override // com.android.volley.Request
    public Response<Gallery> parseNetworkResponse(NetworkResponse networkResponse) {
        Log.i(LOG_TAG, "gallery parse start");
        String body = new String(networkResponse.data, StandardCharsets.UTF_8);
        Gallery gallery;
        try {
            gallery = RedditGalleryResponse.parse(body);
        } catch (JSONException e) {
            Log.w(LOG_TAG, "gallery response malformed");
            return Response.error(new VolleyError("Could not load gallery"));
        }
        if (gallery == null) {
            Log.i(LOG_TAG, "gallery empty");
        } else {
            Log.i(LOG_TAG, "gallery parse success; count=" + gallery.urls.length);
        }
        return Response.success(gallery, CustomHttpHeaderParser.parseCacheEntry(networkResponse, DateUtils.MILLIS_PER_HOUR));
    }

    @Override // com.android.volley.Request
    public VolleyError parseNetworkError(VolleyError volleyError) {
        int status = volleyError != null && volleyError.networkResponse != null
            ? volleyError.networkResponse.statusCode : -1;
        Log.w(LOG_TAG, "gallery request failed; http=" + status);
        return super.parseNetworkError(volleyError);
    }
}
