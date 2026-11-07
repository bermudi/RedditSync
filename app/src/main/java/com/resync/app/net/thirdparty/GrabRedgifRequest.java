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
 * Second step of a RedGifs resolution: GET {@code /v2/gifs/<id>} with the same user agent that
 * minted the temporary token, and read the direct media URL from {@code gif.urls.hd}/{@code sd}.
 * The API-provided URL is returned unchanged; there is no HTML parsing, no synthetic media path,
 * and no /info or client-credentials endpoint. Responses are never disk-cached (they carry
 * token-gated content) and automatic retries are off. On an auth refusal (401/403) of a first
 * attempt, exactly one fresh token is fetched and the metadata request repeated; every other
 * failure is terminal and delivered to the caller once.
 */
public class GrabRedgifRequest extends Request<String> {

    private static final String LOG_TAG = "RedgifsMedia";

    private final RedgifRequestWrapper wrapper;
    private final String metadataUrl;
    private final String userAgent;
    private final boolean preferHd;
    private final boolean alreadyRetried;
    private final String token;

    public GrabRedgifRequest(RedgifRequestWrapper wrapper, String metadataUrl, String userAgent, boolean preferHd, boolean alreadyRetried, String token) {
        super(Method.GET, metadataUrl, wrapper.getErrorListener());
        this.wrapper = wrapper;
        this.metadataUrl = metadataUrl;
        this.userAgent = userAgent;
        this.preferHd = preferHd;
        this.alreadyRetried = alreadyRetried;
        this.token = token;
        setShouldCache(false);
        setRetryPolicy(new DefaultRetryPolicy(10000, 0, 1f));
    }

    @Override // com.android.volley.Request
    public Map<String, String> getHeaders() {
        return RedgifsApi.headers(userAgent, token);
    }

    @Override // com.android.volley.Request
    protected Response<String> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            String url = RedgifsResponse.media(new String(networkResponse.data, StandardCharsets.UTF_8), preferHd);
            return Response.success(url, null);
        } catch (JSONException e) {
            Log.w(LOG_TAG, "redgifs metadata response unusable");
            return Response.error(new VolleyError("Could not read RedGifs media"));
        } catch (IllegalArgumentException e) {
            Log.w(LOG_TAG, "redgifs metadata response unusable");
            return Response.error(new VolleyError("Could not read RedGifs media"));
        }
    }

    @Override // com.android.volley.Request
    protected void deliverResponse(String url) {
        Log.i(LOG_TAG, "redgifs metadata resolved; hd=" + preferHd);
        wrapper.deliverResponse(url);
    }

    @Override // com.android.volley.Request
    public void deliverError(VolleyError error) {
        int status = error != null && error.networkResponse != null ? error.networkResponse.statusCode : -1;
        if (RedgifsApi.shouldRefresh(status, alreadyRetried)) {
            Log.i(LOG_TAG, "redgifs metadata refused; refreshing token; http=" + status);
            RedditApplication.thirdPartyRequestQueue.add(
                new RedgifTokenRequest(wrapper, metadataUrl, userAgent, preferHd, true));
            return;
        }
        Log.w(LOG_TAG, "redgifs metadata request failed; http=" + status);
        wrapper.deliverError(error);
    }
}
