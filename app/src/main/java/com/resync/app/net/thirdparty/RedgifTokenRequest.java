package com.resync.app.net.thirdparty;

import android.util.Log;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.resync.app.RedditApplication;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONException;

/**
 * First step of a RedGifs resolution: fetch a temporary token from
 * {@link RedgifsApi#TOKEN_URL} with the caller's user agent, then enqueue the gif metadata
 * request that spends it. Tokens are short-lived and never disk-cached; no retry happens at this
 * stage (the metadata request decides when a fresh token is worth one more attempt).
 */
public class RedgifTokenRequest extends Request<String> {

    private static final String LOG_TAG = "RedgifsMedia";

    private final RedgifRequestWrapper wrapper;
    private final String metadataUrl;
    private final String userAgent;
    private final boolean preferHd;
    private final boolean alreadyRetried;

    public RedgifTokenRequest(RedgifRequestWrapper wrapper, String metadataUrl, String userAgent, boolean preferHd, boolean alreadyRetried) {
        super(Method.GET, RedgifsApi.TOKEN_URL, wrapper.getErrorListener());
        this.wrapper = wrapper;
        this.metadataUrl = metadataUrl;
        this.userAgent = userAgent;
        this.preferHd = preferHd;
        this.alreadyRetried = alreadyRetried;
        setShouldCache(false);
        setRetryPolicy(new DefaultRetryPolicy(10000, 0, 1f));
    }

    @Override // com.android.volley.Request
    public Map<String, String> getHeaders() {
        return RedgifsApi.headers(userAgent, null);
    }

    @Override // com.android.volley.Request
    protected Response<String> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            String token = RedgifsResponse.token(new String(networkResponse.data, StandardCharsets.UTF_8));
            return Response.success(token, null);
        } catch (JSONException e) {
            Log.w(LOG_TAG, "redgifs token response unusable");
            return Response.error(new VolleyError("Could not read RedGifs token"));
        } catch (IllegalArgumentException e) {
            Log.w(LOG_TAG, "redgifs token response unusable");
            return Response.error(new VolleyError("Could not read RedGifs token"));
        }
    }

    @Override // com.android.volley.Request
    protected void deliverResponse(String token) {
        Log.i(LOG_TAG, "redgifs token acquired; requesting metadata");
        RedditApplication.thirdPartyRequestQueue.add(
            new GrabRedgifRequest(wrapper, metadataUrl, userAgent, preferHd, alreadyRetried, token));
    }

    @Override // com.android.volley.Request
    public void deliverError(VolleyError error) {
        int status = error != null && error.networkResponse != null ? error.networkResponse.statusCode : -1;
        Log.w(LOG_TAG, "redgifs token request failed; http=" + status);
        wrapper.deliverError(error);
    }
}
