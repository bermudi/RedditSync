package com.resync.app.reddit.requests.external.requests.gfycat;

import com.android.volley.Request;
import com.android.volley.Response;
import com.resync.app.net.thirdparty.RedgifsGifRequest;

/* JADX INFO: loaded from: classes.dex */

/**
 * Gfycat shut down and its domains no longer resolve, so the old api.gfycat.com request fails
 * outright. Much of the content moved to RedGifs under the same slug before the shutdown, so
 * this now asks RedGifs for it (see {@link RedgifsGifRequest}); a gif that never moved answers
 * 404 there, the same dead end the old domain gave.
 */
public class GrabGfycatRequest extends RedgifsGifRequest {

    private final String gifUrl;
    private final boolean forceFullQuality;
    private final Response.Listener<String> responseListener;
    private final Response.ErrorListener errorListener;

    public GrabGfycatRequest(String str, boolean z, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        super(str, z, false, errorListener);
        this.gifUrl = str;
        this.forceFullQuality = z;
        this.responseListener = listener;
        this.errorListener = errorListener;
    }

    private GrabGfycatRequest(String str, boolean z, Response.Listener<String> listener, Response.ErrorListener errorListener, boolean isRetry) {
        super(str, z, isRetry, errorListener);
        this.gifUrl = str;
        this.forceFullQuality = z;
        this.responseListener = listener;
        this.errorListener = errorListener;
    }

    @Override // com.resync.app.net.thirdparty.RedgifsGifRequest
    protected Request<String> retryClone() {
        return new GrabGfycatRequest(this.gifUrl, this.forceFullQuality, this.responseListener, this.errorListener, true);
    }

    @Override // com.android.volley.Request
    public void deliverResponse(String str) {
        Response.Listener<String> listener = this.responseListener;
        if (listener != null) {
            listener.onResponse(str);
        }
    }
}
