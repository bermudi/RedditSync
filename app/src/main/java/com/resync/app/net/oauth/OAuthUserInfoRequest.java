package com.resync.app.net.oauth;

import android.content.Context;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.resync.app.singleton.SettingsSingleton;
import org.apache.commons.lang3.time.DateUtils;
import org.json.JSONObject;
import com.resync.app.managers.UserIconManager;
import com.resync.app.net.CustomHttpHeaderParser;
import com.resync.app.util.Logger;
import com.resync.app.util.TextUtils;
import com.resync.app.net.RedditRequest;
import com.resync.app.model.User;

/* JADX INFO: renamed from: y8.c1 */
/* JADX INFO: compiled from: OAuthUserInfoRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthUserInfoRequest extends RedditRequest<User> {
    public OAuthUserInfoRequest(Context context, String str, Response.Listener<User> listener, Response.ErrorListener errorListener) {
        super(context, 0, buildUrl(context, str), listener, errorListener);
        setShouldCache(true);
    }

    private static String buildUrl(Context context, String str) {
        return SettingsSingleton.getOauthUrl(context) + "user/" + str + "/about.json";
    }

    @Override // com.android.volley.Request
    public Response<User> parseNetworkResponse(NetworkResponse networkResponse) {
        try {
            JSONObject jSONObject = new JSONObject(new String(networkResponse.data)).getJSONObject("data");
            String strOptString = jSONObject.optString("name");
            String strOptString2 = jSONObject.optString("icon_img");
            if (!TextUtils.isEmpty(strOptString) && !TextUtils.isEmpty(strOptString2)) {
                UserIconManager.getInstance().setIcon(strOptString, strOptString2.replace("&amp;", "&"));
            }
            return Response.success(new User(), CustomHttpHeaderParser.parseCacheEntry(networkResponse, DateUtils.MILLIS_PER_HOUR));
        } catch (Exception e) {
            Logger.logException(e);
            return Response.error(new ParseError(e));
        }
    }
}
