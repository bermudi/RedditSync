package com.resync.app.net;

import android.content.Context;
import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.errors.OAuthError;
import com.resync.app.RedditApplication;
import com.resync.app.singleton.AccountSingleton;
import java.util.Map;
import com.resync.app.net.OAuthRateHelper;
import com.resync.app.helpers.CrashlyticsHelper;
import com.resync.app.helpers.GoogleAnalyticsHelper;
import com.resync.app.util.Logger;

/* JADX INFO: renamed from: x8.a */
/* JADX INFO: compiled from: RedditRequest.java */
/* JADX INFO: loaded from: classes.dex */
public abstract class RedditRequest<T> extends Request<T> {

    public final String URL_KEY;
    public final String tag;
    protected final Context context;

    protected final Response.Listener<T> responseListener;

    protected String token;

    protected Map<String, String> params;

    // Late-2026 edge wall: reddit 403s thread-shaped endpoints (/comments/{id}.json, …) for the
    // plain HTTP stack, answering with the shreddit web-app HTML instead of JSON, while listings
    // still pass. When that signature arrives we re-fetch the same path through the session
    // WebView (real browser engine + cookies, clears the edge challenge) and feed the body to
    // this request's own parser. One attempt per request.
    private boolean webProxyAttempted;

    public RedditRequest(Context context, int method, String str, Response.Listener<T> listener, Response.ErrorListener errorListener) {
        super(method, str, errorListener);
        this.URL_KEY = "URL";
        this.tag = getClass().getSimpleName();
        this.context = context;
        this.responseListener = listener;
        setShouldCache(false);
        Logger.verbose("URL", getUrl());
    }

    public void setToken(String str) {
        this.token = str;
    }

    @Override // com.android.volley.Request
    public void deliverResponse(T t) {
        if (this.responseListener == null || isCanceled()) {
            return;
        }
        this.responseListener.onResponse(t);
    }

    /**
     * Intercepts error delivery: a 403 whose body is the shreddit web-app HTML means the edge
     * bot-walled this endpoint for the plain HTTP stack, not a real API error. GETs to
     * oauth.reddit.com are transparently retried through the session WebView
     * (see {@link com.resync.app.auth.WebSessionManager#fetchViaWebView}); anything else, or a
     * failed proxy attempt, falls through to the original error listener.
     */
    @Override // com.android.volley.Request
    public void deliverError(VolleyError error) {
        if (!webProxyAttempted && isWebApp403(error) && getMethod() == Method.GET
                && getUrl() != null && getUrl().startsWith("https://oauth.reddit.com/")) {
            webProxyAttempted = true;
            String wwwUrl = getUrl().replaceFirst("^https://oauth\\.reddit\\.com", "https://www.reddit.com");
            // www JSON escapes <>& unless raw_json=1 is set; oauth bodies are always raw.
            wwwUrl += (wwwUrl.contains("?") ? "&" : "?") + "raw_json=1";
            android.util.Log.i("WebView403", "edge 403 (web-app HTML) for " + getUrl() + " -> refetching via WebView");
            try {
                com.resync.app.auth.WebSessionManager.get(this.context).fetchViaWebView(wwwUrl,
                        new com.resync.app.auth.WebSessionManager.FetchCallback() {
                            @Override public void onFetched(int status, String body) {
                                Response<T> parsed = null;
                                String failure = null;
                                if (status == 200 && body != null && !body.isEmpty()
                                        && (body.charAt(0) == '{' || body.charAt(0) == '[')) {
                                    try {
                                        parsed = parseNetworkResponse(new NetworkResponse(200,
                                                body.getBytes("UTF-8"), new java.util.HashMap<String, String>(), false));
                                    } catch (Exception e) {
                                        failure = "proxy parse dispatch: " + e;
                                    }
                                } else {
                                    failure = "proxy HTTP " + status + ": "
                                            + (body == null ? "" : body.substring(0, Math.min(80, body.length())).replace('\n', ' '));
                                }
                                if (parsed != null && parsed.isSuccess()) {
                                    android.util.Log.i("WebView403", "proxied ok: " + getUrl());
                                    deliverResponse(parsed.result);
                                } else {
                                    android.util.Log.i("WebView403", failure != null ? failure : "proxy parse failed");
                                    RedditRequest.this.deliverError(new VolleyError(failure != null ? failure : "web proxy refetch failed"));
                                }
                            }
                            @Override public void onError(String reason) {
                                android.util.Log.i("WebView403", "proxy error: " + reason);
                                RedditRequest.this.deliverError(new VolleyError("web proxy: " + reason));
                            }
                        });
                return; // swallowed the 403; the proxy callbacks deliver the real outcome
            } catch (Exception e) {
                android.util.Log.i("WebView403", "dispatch failed: " + e);
                // fall through and deliver the original error
            }
        }
        super.deliverError(error);
    }

    /** True for the edge bot-wall signature: HTTP 403 whose body is HTML (the web-app shell). */
    private static boolean isWebApp403(VolleyError error) {
        if (error == null || error.networkResponse == null) {
            return false;
        }
        if (error.networkResponse.statusCode != 403 || error.networkResponse.data == null
                || error.networkResponse.data.length == 0) {
            return false;
        }
        int n = Math.min(64, error.networkResponse.data.length);
        String head = new String(error.networkResponse.data, 0, n).trim();
        return head.startsWith("<body") || head.startsWith("<!DOCTYPE") || head.startsWith("<html");
    }

    @Override // com.android.volley.Request
    public Map<String, String> getHeaders() throws AuthFailureError {
        return AccountSingleton.getAuthHeaders(this.token);
    }

    @Override // com.android.volley.Request
    protected Map<String, String> getParams() throws AuthFailureError {
        return this.params;
    }

    public boolean isRateLimited() {
        return !OAuthRateHelper.isWithinRateLimit(getUrl());
    }

    @Override // com.android.volley.Request
    public VolleyError parseNetworkError(VolleyError volleyError) {
        if (volleyError == null) {
            CrashlyticsHelper.logEvent(6, "Volley", "No error returned!");
            return super.parseNetworkError(volleyError);
        }
        try {
            Logger.verbose("RedditRequest", new String(volleyError.networkResponse.data));
        } catch (Exception unused) {
            Logger.verbose("RedditRequest", "No data returned");
        }
        NetworkResponse networkResponse = volleyError.networkResponse;
        if (networkResponse != null) {
            Map<String, String> map = networkResponse.headers;
            if (map.get("www-authenticate") != null && map.get("www-authenticate").equals("Bearer realm=\"reddit\", error=\"invalid_token\"")) {
                GoogleAnalyticsHelper.trackEvent("Errors", "OAuth", "Invalid token");
                RequestQueue requestQueue = RedditApplication.oauthRequestQueue;
                if (requestQueue != null && requestQueue.getCache() != null) {
                    RedditApplication.mainRequestQueue.getCache().clear();
                }
                return new OAuthError(volleyError);
            }
        }
        return super.parseNetworkError(volleyError);
    }
}
