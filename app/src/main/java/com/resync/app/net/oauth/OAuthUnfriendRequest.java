package com.resync.app.net.oauth;

import android.content.Context;
import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.resync.app.RedditApplication;
import com.resync.app.singleton.SettingsSingleton;
import java.util.HashMap;
import com.resync.app.net.RedditRequest;

/* JADX INFO: renamed from: y8.a1 */
/* JADX INFO: compiled from: OAuthUnfriendRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthUnfriendRequest extends RedditRequest<Void> {
    public OAuthUnfriendRequest(Context context, String str) {
        super(context, 3, buildUrl(context, str), null, null);
        HashMap map = new HashMap();
        this.params = map;
        map.put("id", str);
        RedditApplication.oauthRequestQueue.getCache().remove("https://oauth.reddit.com/user/" + str + "/about.json");
    }

    public static String buildUrl(Context context, String str) {
        StringBuilder sb2 = new StringBuilder();
        sb2.append(SettingsSingleton.getOauthUrl(context));
        sb2.append("api/v1/me/friends/" + str);
        return sb2.toString();
    }

    @Override // com.android.volley.Request
    public Response<Void> parseNetworkResponse(NetworkResponse networkResponse) {
        return Response.success(null, HttpHeaderParser.parseCacheHeaders(networkResponse));
    }
}
