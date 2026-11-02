package com.resync.app.net.oauth;

import android.content.ContentValues;
import android.content.Context;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.resync.app.provider.RedditProvider;
import com.resync.app.singleton.SettingsSingleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.resync.app.text.parsing.ContentValuesListHelper;
import com.resync.app.text.parsing.PostParserHelper;
import com.resync.app.net.OAuthRequestManager;
import com.resync.app.util.Logger;
import com.resync.app.net.RedditRequest;

/* JADX INFO: renamed from: y8.f */
/* JADX INFO: compiled from: OAuthCommentsMoreRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthCommentsMoreRequest extends RedditRequest<Void> {
    private final String listKey;

    private final String fullname;
    private final String parentFullname;
    private final String childrenIds;
    private final int position;
    private final int page;
    ArrayList<String> authorFullnames;

    class AuthorInfoResponseListener implements Response.Listener<Void> {

        final /* synthetic */ Void pendingResult;

        AuthorInfoResponseListener(Void result) {
            this.pendingResult = result;
        }

        @Override // com.android.volley.Response.Listener
        public void onResponse(Void result) {
            if (OAuthCommentsMoreRequest.this.responseListener != null) {
                OAuthCommentsMoreRequest.this.responseListener.onResponse(this.pendingResult);
            }
        }
    }

    class AuthorInfoErrorListener implements Response.ErrorListener {

        final /* synthetic */ Void pendingResult;

        AuthorInfoErrorListener(Void result) {
            this.pendingResult = result;
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            if (OAuthCommentsMoreRequest.this.responseListener != null) {
                OAuthCommentsMoreRequest.this.responseListener.onResponse(this.pendingResult);
            }
        }
    }

    public OAuthCommentsMoreRequest(Context context, String str, String str2, String str3, String str4, String str5, int position, int page, String str6, Response.Listener<Void> listener, Response.ErrorListener errorListener) {
        // Reddit no longer answers api/morechildren for third-party clients, so this request reads
        // the comments thread instead (/comments/{link} for a top-level "more", /comments/{link}/_/{parent}
        // for a nested one) and cuts the reply back down to the flat "things" list morechildren
        // returned — see extractThings() below. Sent as a GET listing fetch.
        super(context, 0, buildUrl(context, str2, str3, str6), listener, errorListener);
        this.authorFullnames = new ArrayList<>();
        this.listKey = str;
        this.fullname = str4;
        this.parentFullname = StringUtils.isNotEmpty(str3) ? str3 : "t3_" + str2;
        this.childrenIds = str5;
        this.position = position;
        this.page = page;
        Logger.verbose("t3_" + str2);
        Logger.verbose(str5);
        Logger.verbose("Sort: " + str6);
    }

    private static String buildUrl(Context context, String linkId, String parentFullname, String sort) {
        boolean topLevel = parentFullname == null || !parentFullname.startsWith("t1_");
        StringBuilder sb = new StringBuilder(SettingsSingleton.getOauthUrl(context)).append("comments/").append(linkId);
        if (!topLevel) {
            sb.append("/_/").append(parentFullname.substring(3));
        }
        // Reddit caps one fetch at 500 comments. At the top level spend them on top-level comments
        // and their first replies; deeper replies come back as their own "load more" (the nested
        // case, which loads fully).
        sb.append(".json?always_show_media=1&profile_img=true&limit=500&depth=").append(topLevel ? 2 : 10);
        if (StringUtils.isNotEmpty(sort) && !"suggested".equalsIgnoreCase(sort)) {
            sb.append("&sort=").append(sort.toLowerCase(Locale.ENGLISH).replace("&", ""));
        }
        return sb.toString();
    }

    /**
     * Cuts a thread listing down to the flat list of comments morechildren would have returned: the
     * requested comments (by id) plus everything under them, pre-order like morechildren so the
     * indentation Sync derives from the order still works. Deeper "more" nodes under added comments
     * are kept so they can be loaded in turn.
     */
    private static JSONArray extractThings(JSONArray threadChildren, String parentFullname, String childrenIds) throws JSONException {
        Set<String> wanted = new HashSet<>(Arrays.asList(childrenIds.split(",")));
        Set<String> shown = new HashSet<>();
        shown.add(parentFullname);
        JSONArray things = new JSONArray();
        if (walk(threadChildren, parentFullname, wanted, shown, things) == 0) {
            // Nothing new within Reddit's 500 comment window (bottom of a huge thread).
            return null;
        }
        return things;
    }

    private static int walk(JSONArray children, String parent, Set<String> wanted, Set<String> shown, JSONArray things) throws JSONException {
        int added = 0;
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            JSONObject data = child == null ? null : child.optJSONObject("data");
            if (data == null) {
                continue;
            }
            String parentId = data.optString("parent_id");
            String kind = child.optString("kind");
            if ("t1".equals(kind)) {
                JSONObject replies = data.optJSONObject("replies");
                // Like morechildren: the requested comments plus everything under them.
                boolean underAdded = shown.contains(parentId) && !parentId.equals(parent);
                if (underAdded || (wanted.contains(data.optString("id")) && shown.contains(parentId))) {
                    data.put("replies", "");
                    things.put(child);
                    shown.add(data.optString("name"));
                    added++;
                }
                JSONObject repliesData = replies == null ? null : replies.optJSONObject("data");
                JSONArray sub = repliesData == null ? null : repliesData.optJSONArray("children");
                if (sub != null) {
                    added += walk(sub, parent, wanted, shown, things);
                }
            } else if ("more".equals(kind) && shown.contains(parentId)) {
                // A deeper "load more" under a comment we added: keep it. One directly under the
                // parent is only useful while it still holds requested comments.
                if (!parentId.equals(parent) || overlaps(data.optJSONArray("children"), wanted)) {
                    things.put(child);
                }
            }
        }
        return added;
    }

    private static boolean overlaps(JSONArray ids, Set<String> wanted) {
        if (ids == null) {
            return false;
        }
        for (int i = 0; i < ids.length(); i++) {
            if (wanted.contains(ids.optString(i))) {
                return true;
            }
        }
        return false;
    }

    @Override // com.resync.app.net.RedditRequest, com.android.volley.Request
    public void deliverResponse(Void result) {
        if (this.authorFullnames.size() > 0) {
            OAuthRequestManager.sendWithAppContext(new OAuthAuthorInfoRequest(this.context, this.authorFullnames, new AuthorInfoResponseListener(result), new AuthorInfoErrorListener(result)));
        } else {
            super.deliverResponse(result);
        }
    }

    public String getFullname() {
        return this.fullname;
    }

    @Override // com.android.volley.Request
    public Response<Void> parseNetworkResponse(NetworkResponse networkResponse) {
        int thingIndexCopy;
        try {
            String str = new String(networkResponse.data);
            Logger.verbose(str);
            // A thread replies with [postListing, commentsListing]; the comments are second. For a
            // nested "more" that listing holds only the parent's subtree, already in depth order.
            JSONArray threadChildren = new JSONArray(str).getJSONObject(1).getJSONObject("data").getJSONArray("children");
            JSONArray jSONArray = extractThings(threadChildren, this.parentFullname, this.childrenIds);
            if (jSONArray == null) {
                return Response.error(new VolleyError("No more comments within Reddit's fetch window"));
            }
            int length = jSONArray.length();
            ArrayList<ContentValues> arrayList = new ArrayList();
            int thingIndex = 0;
            while (thingIndex < length) {
                if (jSONArray.getJSONObject(thingIndex).getString("kind").equals("t1")) {
                    thingIndexCopy = thingIndex;
                    PostParserHelper.parsePost(this.context, 1, jSONArray.getJSONObject(thingIndex).getJSONObject("data"), arrayList, this.page, true, true, null);
                } else {
                    thingIndexCopy = thingIndex;
                    if (jSONArray.getJSONObject(thingIndexCopy).getString("kind").equals("more")) {
                        PostParserHelper.parsePost(this.context, 9, jSONArray.getJSONObject(thingIndexCopy).getJSONObject("data"), arrayList, this.page, true, true, null);
                    }
                }
                thingIndex = thingIndexCopy + 1;
            }
            if (SettingsSingleton.getSettings().highlightNewAccounts) {
                for (ContentValues contentValues : arrayList) {
                    if (contentValues.containsKey("post_author_fullname")) {
                        String asString = contentValues.getAsString("post_author_fullname");
                        if (StringUtils.isNotEmpty(asString) && !this.authorFullnames.contains(asString)) {
                            Logger.verbose("Author: " + asString);
                            this.authorFullnames.add(asString);
                        }
                    }
                }
            }
            if (!isCanceled()) {
                ContentValues[] contentValuesArrA = ContentValuesListHelper.attachListMetadata(arrayList, this.listKey, this.position);
                this.context.getContentResolver().delete(RedditProvider.URI_DELETE_LIST_ITEM, this.fullname, null);
                this.context.getContentResolver().update(RedditProvider.URI_UPDATE_COMMENTS_LIST_TIME, null, null, new String[]{this.listKey, Integer.toString(this.position), Integer.toString(length - 1)});
                Logger.verbose("Inserted: " + this.context.getContentResolver().bulkInsert(RedditProvider.URI_INSERT_COMMENTS, contentValuesArrA));
                this.context.getContentResolver().notifyChange(RedditProvider.URI_MULTIPLE_POSTS, null);
            }
            return Response.success(null, HttpHeaderParser.parseCacheHeaders(networkResponse));
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }
}
