package com.resync.app.net.oauth;

import android.content.Context;
import android.graphics.Color;
import android.text.format.DateFormat;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.bumptech.glide.MaterialPostsHelper;
import com.bumptech.glide.RedditHelper;
import com.resync.app.RedditApplication;
import com.resync.app.singleton.SettingsSingleton;
import java.util.Calendar;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.json.JSONObject;
import com.resync.app.managers.SubredditColorManager;
import com.resync.app.managers.SubredditIconManager;
import com.resync.app.managers.UserIconManager;
import com.resync.app.net.CustomHttpHeaderParser;
import com.resync.app.events.IconsUpdatedEvent;
import com.resync.app.singletons.BusSingleton;
import com.resync.app.helpers.TimestampHelper;
import com.resync.app.util.Logger;
import com.resync.app.util.TextUtils;
import com.resync.app.util.WebsafeUtils;
import com.resync.app.net.RedditRequest;
import com.resync.app.net.SubredditNotFoundError;
import com.resync.app.model.Data;
import com.resync.app.model.Subreddit;

/* JADX INFO: renamed from: y8.y0 */
/* JADX INFO: compiled from: OAuthSubredditInfoRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthSubredditInfoRequest extends RedditRequest<Subreddit> {
    private final String subreddit;

    public OAuthSubredditInfoRequest(Context context, Object obj, String str, Response.Listener<Subreddit> listener, Response.ErrorListener errorListener) {
        super(context, 0, buildUrl(context, str), listener, errorListener);
        this.subreddit = str;
        Logger.verbose("CONSTRUCTING URL FROM: " + getUrl());
        if (obj != null) {
            setTag(obj);
        }
        setShouldCache(false);
    }

    private static String buildUrl(Context context, String str) {
        Logger.verbose("CONSTRUCTING URL FROM: " + str);
        if (!MaterialPostsHelper.isUserProfile(str)) {
            return SettingsSingleton.getOauthUrl(context) + "r/" + str + "/about.json";
        }
        return SettingsSingleton.getOauthUrl(context) + "user/" + MaterialPostsHelper.extractProfileUsername(str) + "/about.json";
    }

    @Override // com.resync.app.net.RedditRequest, com.android.volley.Request
    /* JADX INFO: renamed from: c, reason: merged with bridge method [inline-methods] */
    public void deliverResponse(Subreddit subreddit) {
        super.deliverResponse(subreddit);
        BusSingleton.getBus().post(new IconsUpdatedEvent());
    }

    @Override // com.android.volley.Request
    public Request.Priority getPriority() {
        return Request.Priority.LOW;
    }

    @Override // com.android.volley.Request
    public Response<Subreddit> parseNetworkResponse(NetworkResponse networkResponse) {
        JSONObject jSONObjectOptJSONObject;
        try {
            String str = new String(networkResponse.data);
            Logger.verbose("JSON: " + str);
            if (str.contains("\"kind\": \"Listing\"")) {
                return Response.error(new SubredditNotFoundError());
            }
            JSONObject jSONObject = new JSONObject(str);
            Subreddit subreddit = new Subreddit();
            subreddit.name = this.subreddit;
            if (jSONObject.has("error")) {
                subreddit.errorCode = jSONObject.getInt("error");
            }
            if (jSONObject.has("kind")) {
                subreddit.kind = jSONObject.getString("kind");
            }
            if (jSONObject.has("data")) {
                JSONObject jSONObject2 = jSONObject.getJSONObject("data");
                Data data = new Data();
                subreddit.data = data;
                data.id = jSONObject2.optString("id");
                if (jSONObject2.has("created_utc")) {
                    long createdUtc = jSONObject2.getLong("created_utc");
                    subreddit.data.accountAge = TimestampHelper.getAccountAgeString(createdUtc);
                    subreddit.data.formattedDate = TimestampHelper.getFormattedDate(createdUtc);
                    Calendar calendar = Calendar.getInstance(Locale.ENGLISH);
                    calendar.setTimeInMillis(createdUtc * 1000);
                    subreddit.data.joinedDate = "Joined on " + DateFormat.format("yyyy/MM/dd", calendar).toString();
                }
                if (jSONObject2.has("user_flair_template_id")) {
                    subreddit.data.userFlairTemplateId = jSONObject2.optString("user_flair_template_id");
                }
                if (jSONObject2.has("total_karma")) {
                    subreddit.data.totalKarma = jSONObject2.getInt("total_karma");
                }
                if (jSONObject2.has("link_karma")) {
                    subreddit.data.linkKarma = jSONObject2.getInt("link_karma");
                }
                if (jSONObject2.has("comment_karma")) {
                    subreddit.data.commentKarma = jSONObject2.getInt("comment_karma");
                }
                if (jSONObject2.has("over18")) {
                    subreddit.data.over18 = jSONObject2.getBoolean("over18");
                }
                if (jSONObject2.has("is_friend")) {
                    subreddit.data.isFriend = jSONObject2.getBoolean("is_friend");
                }
                if (jSONObject2.has("name")) {
                    subreddit.data.name = jSONObject2.getString("name");
                }
                if (jSONObject2.has("description")) {
                    subreddit.data.descriptionHtml = RedditHelper.processMarkdownToHtml(jSONObject2.optString("name"), jSONObject2.getString("description"));
                }
                if (jSONObject2.has("subreddit") && (jSONObjectOptJSONObject = jSONObject2.optJSONObject("subreddit")) != null && jSONObjectOptJSONObject.has("public_description")) {
                    subreddit.data.publicDescriptionHtml = RedditHelper.processMarkdownToHtml(null, jSONObjectOptJSONObject.getString("public_description"));
                }
                if (jSONObject2.has("key_color")) {
                    subreddit.data.keyColor = jSONObject2.getString("key_color");
                    if (!TextUtils.isEmpty(subreddit.data.keyColor)) {
                        SubredditColorManager.getInstance().setColor(this.subreddit, Integer.valueOf(Color.parseColor(subreddit.data.keyColor)));
                    }
                }
                if (jSONObject2.has("banner_background_color")) {
                    subreddit.data.bannerBackgroundColor = jSONObject2.getString("banner_background_color");
                }
                if (jSONObject2.has("banner_background_image")) {
                    subreddit.data.bannerBackgroundImage = WebsafeUtils.unescapeHtml(jSONObject2.getString("banner_background_image"));
                }
                if (StringUtils.isEmpty(subreddit.data.bannerBackgroundImage) && jSONObject2.has("banner_img")) {
                    subreddit.data.bannerBackgroundImage = WebsafeUtils.unescapeHtml(jSONObject2.getString("banner_img"));
                }
                if (MaterialPostsHelper.isUserProfile(this.subreddit)) {
                    String strOptString = jSONObject2.optString("icon_img");
                    if (!TextUtils.isEmpty(strOptString)) {
                        UserIconManager.getInstance().setIcon(MaterialPostsHelper.extractProfileUsername(this.subreddit), WebsafeUtils.unescapeHtml(strOptString));
                    }
                } else {
                    String strOptString2 = jSONObject2.optString("community_icon");
                    if (TextUtils.isEmpty(strOptString2)) {
                        String strOptString3 = jSONObject2.optString("icon_img");
                        if (!TextUtils.isEmpty(strOptString3)) {
                            SubredditIconManager.getInstance().setIcon(this.subreddit, WebsafeUtils.unescapeHtml(strOptString3));
                            subreddit.data.iconUrl = WebsafeUtils.unescapeHtml(strOptString3);
                        }
                    } else {
                        SubredditIconManager.getInstance().setIcon(this.subreddit, WebsafeUtils.unescapeHtml(strOptString2));
                        subreddit.data.iconUrl = WebsafeUtils.unescapeHtml(strOptString2);
                    }
                }
                if (jSONObject2.has("display_name")) {
                    subreddit.data.displayName = jSONObject2.getString("display_name");
                }
                subreddit.data.userIsSubscriber = jSONObject2.optBoolean("user_is_subscriber");
                subreddit.data.userHasFavorited = jSONObject2.optBoolean("user_has_favorited");
                subreddit.data.subscribers = jSONObject2.optInt("subscribers");
                subreddit.data.accountsActive = jSONObject2.optInt("accounts_active");
            }
            return Response.success(subreddit, CustomHttpHeaderParser.parseCacheEntry(networkResponse, DateUtils.MILLIS_PER_HOUR));
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }

    public OAuthSubredditInfoRequest(String str, Response.Listener<Subreddit> listener, Response.ErrorListener errorListener) {
        super(RedditApplication.getAppContext(), 0, SettingsSingleton.getOauthUrl() + "user/" + str + "/about.json", listener, errorListener);
        this.subreddit = MaterialPostsHelper.buildOverviewProfileAddress(str);
        setShouldCache(true);
    }
}
