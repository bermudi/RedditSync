package com.resync.app.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.resync.app.auth.WebSessionManager;

/**
 * adb-only diagnostic: fetches an arbitrary URL through the session WebView (the one client that
 * passes reddit's edge wall) and logs status + body head. Used to probe which endpoint shapes
 * still serve data.
 *
 * <pre>
 * adb shell am broadcast -a com.resync.app.DEBUG_FETCH \
 *   -d "https://www.reddit.com/svc/shreddit/comments/r/aww/t3_1xma5sz?sort=top" \
 *   --es accept "text/html" --ez auth false com.resync.app
 * </pre>
 */
public class DebugFetchReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String url = intent.getStringExtra("url");
        if (url == null) {
            android.util.Log.i("DebugFetch", "no url");
            return;
        }
        String accept = intent.getStringExtra("accept");
        if (accept == null || accept.isEmpty()) {
            accept = "application/json";
        }
        boolean auth = intent.getBooleanExtra("auth", false);
        String eval = intent.getStringExtra("eval");
        android.util.Log.i("DebugFetch", "fetching " + url + " accept=" + accept + " auth=" + auth
                + (eval != null ? " eval=len" + eval.length() : ""));
        WebSessionManager mgr = WebSessionManager.get(context);
        WebSessionManager.FetchCallback cb = new WebSessionManager.FetchCallback() {
            @Override public void onFetched(int status, String body) {
                android.util.Log.i("DebugFetch", "RESULT " + status + " len=" + body.length()
                        + " head=" + body.substring(0, Math.min(1200, body.length())).replace('\n', ' '));
            }
            @Override public void onError(String reason) {
                android.util.Log.i("DebugFetch", "ERROR " + reason);
            }
        };
        if (eval != null) {
            String wait = intent.getStringExtra("wait");
            mgr.evalOnPage(url, eval, wait, cb);
        } else {
            mgr.fetchViaWebView(url, accept, auth, cb);
        }
    }
}
