package com.resync.app.net.thirdparty;

import com.android.volley.Response;
import com.android.volley.VolleyError;

/**
 * Carries one RedGifs resolution across the token and metadata request chain. The short-lived
 * token stays inside the chain (request-local), so nothing token-shaped is stored here.
 */
public class RedgifRequestWrapper {

    public final String gifUrl;
    public final boolean forceHd;

    private final Response.Listener<String> responseListener;
    private final Response.ErrorListener errorListener;

    public RedgifRequestWrapper(String str, boolean forceHd, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        this.gifUrl = str;
        this.forceHd = forceHd;
        this.responseListener = listener;
        this.errorListener = errorListener;
    }

    public void deliverError(VolleyError volleyError) {
        Response.ErrorListener errorListener = this.errorListener;
        if (errorListener != null) {
            errorListener.onErrorResponse(volleyError);
        }
    }

    public void deliverResponse(String str) {
        Response.Listener<String> listener = this.responseListener;
        if (listener != null) {
            listener.onResponse(str);
        }
    }

    public Response.ErrorListener getErrorListener() {
        return this.errorListener;
    }

    public boolean isForceHd() {
        return this.forceHd;
    }

    public String getGifUrl() {
        return this.gifUrl;
    }
}
