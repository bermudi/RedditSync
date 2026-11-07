package com.resync.app.net;

import android.content.Context;
import android.util.Log;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.resync.app.RedditApplication;
import com.resync.app.helpers.DataSavingHelper;
import com.resync.app.net.oauth.OAuthTempTokenExchangeRequest;
import com.resync.app.net.oauth.OAuthTokenExchangeRequest;
import com.resync.app.net.thirdparty.RedgifRequestWrapper;
import com.resync.app.net.thirdparty.RedgifsApi;
import com.resync.app.net.thirdparty.RedgifTokenRequest;
import com.resync.app.singleton.AccountSingleton;
import com.resync.app.util.Logger;
import com.resync.app.util.TextUtils;
import com.resync.app.net.RedditRequest;

/* JADX INFO: renamed from: m8.a */
/* JADX INFO: compiled from: OAuthRequestManager.java */
/* JADX INFO: loaded from: classes.dex */
public final class OAuthRequestManager {

    static final String TAG = OAuthRequestManager.class.toString();

    static class CancelAllFilterMain implements RequestQueue.RequestFilter {
        CancelAllFilterMain() {
        }

        @Override // com.android.volley.RequestQueue.RequestFilter
        public boolean apply(Request<?> request) {
            return true;
        }
    }

    static class CancelAllFilterAuthenticated implements RequestQueue.RequestFilter {
        CancelAllFilterAuthenticated() {
        }

        @Override // com.android.volley.RequestQueue.RequestFilter
        public boolean apply(Request<?> request) {
            return true;
        }
    }

    static class CancelAllFilterAux implements RequestQueue.RequestFilter {
        CancelAllFilterAux() {
        }

        @Override // com.android.volley.RequestQueue.RequestFilter
        public boolean apply(Request<?> request) {
            return true;
        }
    }

    private OAuthRequestManager() {
    }

    public static void enqueueAux(Request request) {
        if (request == null) {
            return;
        }
        RedditApplication.thirdPartyRequestQueue.add(request);
    }

    public static void sendAuthenticated(Context context, String str, final RedditRequest redditRequest) {
        Logger.verbose("CONTEXT: " + context);
        if (context == null || redditRequest == null) {
            return;
        }
        if (!TextUtils.isEmpty(str)) {
            redditRequest.setToken(str);
            RedditApplication.oauthRequestQueue.add(redditRequest);
        } else {
            // Sync's original OAuth-app token exchanges (ssl.reddit.com/api/v1/access_token, both the
            // logged-in authorize flow AND the logged-OUT installed_client grant) are dead — Reddit
            // retired third-party app keys, so both 401. Instead we mint a bearer from reddit.com's own
            // web session via WebSessionManager, exactly like the reddit.com web client does:
            //   * logged IN  -> a user bearer (reddit_session cookie present);
            //   * logged OUT -> an ANONYMOUS bearer (only loid+csrf cookies). Verified to read /best,
            //     subreddit listings and comment threads from oauth.reddit.com — so logged-out users
            //     see the front page and can browse, instead of the old "Error loading page: 401".
            // Either way the token attaches to oauth.reddit.com (SettingsSingleton.getOauthUrl()).
            com.resync.app.auth.WebSessionManager.get(context).getToken(
                new com.resync.app.auth.WebSessionManager.TokenCallback() {
                    @Override
                    public void onToken(String jwt) {
                        redditRequest.setToken(jwt);
                        RedditApplication.oauthRequestQueue.add(redditRequest);
                    }
                    @Override
                    public void onError(String reason) {
                        Logger.verbose("Web-session token unavailable: " + reason);
                        // No token (anon mint failed too) -> deliver an auth error so the UI shows retry.
                        redditRequest.deliverError(new com.android.volley.AuthFailureError(reason));
                    }
                });
        }
    }

    public static void send(Context context, RedditRequest redditRequest) {
        sendAuthenticated(context, null, redditRequest);
    }

    public static void sendWithAppContext(RedditRequest redditRequest) {
        sendAuthenticated(RedditApplication.getAppContext(), null, redditRequest);
    }

    public static synchronized void sendRedgif(RedgifRequestWrapper redgifRequestWrapper) {
        // RedGifs v2 protocol: validate the public link, mint a short-lived temporary token with the
        // app's user agent, then request /v2/gifs/<id> metadata with that same agent and read
        // gif.urls.hd/sd. Each resolution mints its own token (one extra small request) instead of
        // caching IP-bound token state; revisit a shared cache only with device evidence.
        try {
            String metadataUrl = RedgifsApi.metadataUrl(redgifRequestWrapper.getGifUrl());
            String userAgent = AccountSingleton.getUserAgent();
            boolean preferHd = redgifRequestWrapper.isForceHd() || !DataSavingHelper.shouldSaveData();
            RedditApplication.thirdPartyRequestQueue.add(
                new RedgifTokenRequest(redgifRequestWrapper, metadataUrl, userAgent, preferHd, false));
        } catch (IllegalArgumentException e) {
            Log.w("RedgifsMedia", "redgifs link rejected");
            redgifRequestWrapper.deliverError(new VolleyError("Invalid RedGifs link"));
        }
    }

    public static void enqueueDirect(RedditRequest redditRequest) {
        if (redditRequest == null) {
            return;
        }
        RedditApplication.oauthRequestQueue.add(redditRequest);
    }

    public static void sendWithTempTokenExchange(Context context, RedditRequest redditRequest, String str) {
        if (TextUtils.isEmpty(str)) {
            Logger.verbose("Skipping temp token exchange!");
            sendAuthenticated(context, null, redditRequest);
        } else {
            Logger.verbose("Performing temp token exchange!");
            RedditApplication.thirdPartyRequestQueue.add(new OAuthTempTokenExchangeRequest(context, redditRequest, str));
        }
    }

    public static void cancelAll() {
        Logger.verbose("Cleaning up the request queues!");
        RedditApplication.mainRequestQueue.cancelAll((RequestQueue.RequestFilter) new CancelAllFilterMain());
        RedditApplication.oauthRequestQueue.cancelAll((RequestQueue.RequestFilter) new CancelAllFilterAuthenticated());
        RedditApplication.thirdPartyRequestQueue.cancelAll((RequestQueue.RequestFilter) new CancelAllFilterAux());
    }
}
