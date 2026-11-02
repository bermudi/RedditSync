package com.resync.app.net.oauth;

import android.content.Context;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.resync.app.singleton.SettingsSingleton;
import org.json.JSONException;
import org.json.JSONObject;
import com.resync.app.net.RedditRequest;

/* JADX INFO: renamed from: y8.b1 */
/* JADX INFO: compiled from: OAuthUserIdRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthUserIdRequest extends RedditRequest<String> {
    public OAuthUserIdRequest(Context context, String str, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        super(context, 0, SettingsSingleton.getOauthUrl(context) + "user/" + str + "/about.json", listener, errorListener);
    }

    @Override // com.android.volley.Request
    public Response<String> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            return Response.success(new JSONObject(new String(networkResponse.data)).getJSONObject("data").getString("id"), HttpHeaderParser.parseCacheHeaders(networkResponse));
        } catch (JSONException e) {
            return Response.error(new ParseError(e));
        }
    }
}
