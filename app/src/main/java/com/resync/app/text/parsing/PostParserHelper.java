package com.resync.app.text.parsing;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Patterns;
import com.bumptech.glide.RedditHelper;
import com.resync.app.singleton.SettingsSingleton;
import com.resync.app.singleton.UiSingleton;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.resync.app.media.images.PostPreviewHelper;
import com.resync.app.media.images.PostThumbnailHelper;
import com.resync.app.media.images.PostValuehelper;
import com.resync.app.media.images.SyncTypeHelper;
import com.resync.app.managers.ModeratedSubredditManager;
import com.resync.app.managers.SubredditColorManager;
import com.resync.app.managers.SubredditIconManager;
import com.resync.app.db.RedditDb;
import com.resync.app.helpers.FilterHelper;
import com.resync.app.helpers.TimestampHelper;
import com.resync.app.helpers.WindowUtils;
import com.resync.app.widgets.preview.AbstractRedditPreview;
import com.resync.app.widgets.preview.ImagePreview;
import com.resync.app.widgets.preview.RedditAlbumPreview;
import com.resync.app.widgets.preview.RedditImagePreview;
import com.resync.app.widgets.preview.RedditLinkPreview;
import com.resync.app.util.Logger;
import com.resync.app.util.WebsafeUtils;
import com.resync.app.media.glide.GlideHelper;
import com.resync.app.util.links.LinkTypeHelper;

/* JADX INFO: renamed from: c9.c */
/* JADX INFO: compiled from: PostParserHelper.java */
/* JADX INFO: loaded from: classes.dex */
public abstract class PostParserHelper {
    public static void parsePost(Context var0, int var1, JSONObject var2, ArrayList<ContentValues> var3, int var4, boolean var5, boolean var6, String var7) throws JSONException {
        if (var1 == 1 || var1 == 3 || var1 == 9) {
            if (var0 != null && var2 != null && var3 != null) {
                ContentValues var26 = new ContentValues();
                String var21 = "id";
                String var25 = "link_flair_text";
                Exception var172 = null;
                var26.put("_id", var2.getString("id"));
                var26.put("kind", var1);
                var26.put("parent_id", var2.optString("parent_id"));
                var26.put("hash", var2.getString("id").hashCode());
                if (var1 == 1) {
                    var26.put("link_id", var2.getString("link_id").split("_")[1]);
                }

                var26.put("age", RedditDb.now());
                var26.put("likes", PostValuehelper.parseBoolean(var2.optString("likes")));
                var26.put("num_reports", var2.optInt("num_reports", 0));
                var26.put("num_comments", var2.optInt("num_comments"));
                if (var2.has("gildings")) {
                    var26.put("gilded", var2.getJSONObject("gildings").optInt("gid_2"));
                    var26.put("silvered", var2.getJSONObject("gildings").optInt("gid_1"));
                    var26.put("platinumed", var2.getJSONObject("gildings").optInt("gid_3"));
                }

                int var10 = var2.optInt("total_awards_received");
                boolean var14;
                if (var10 > 0) {
                    var14 = true;
                } else {
                    var14 = false;
                }

                var26.put("has_awards", var14);
                var26.put("award_count", var10);
                String var19 = "url";
                if (var10 > 0) {
                    JSONArray var17 = var2.optJSONArray("all_awardings");
                    ArrayList var18 = new ArrayList();
                    if (var17 != null) {
                        for (int var51 = 0; var51 < var17.length(); var51++) {
                            JSONObject var22 = var17.getJSONObject(var51);
                            if (var22 != null) {
                                JSONArray var20 = var22.getJSONArray("resized_static_icons");
                                if (var20 != null && !var20.toString().contains("external-preview") && var20.length() > 3) {
                                    String var167 = var20.getJSONObject(3).getString("url");
                                    if (StringUtils.isNotEmpty(var167)) {
                                        var18.add(WebsafeUtils.unescapeHtml(var167));
                                    }
                                } else {
                                    var20 = var22.getJSONArray("resized_icons");
                                    if (var20 != null && var20.length() > 3) {
                                        String var166 = var20.getJSONObject(3).getString("url");
                                        if (StringUtils.isNotEmpty(var166)) {
                                            var18.add(WebsafeUtils.unescapeHtml(var166));
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (var18.size() > 0) {
                        String var105 = StringUtils.join(var18, ",");
                        var26.put("post_award_string", var105);
                        Logger.verbose("AWARD: " + var105);
                    }
                }

                var26.put("controversiality", var2.optInt("controversiality"));
                var26.put("score", var2.optInt("score"));
                var26.put("view_count", var2.optInt("view_count", 0));
                if (var1 == 1 || var1 == 9) {
                    if (!var5) {
                        var26.put("level", var4);
                    } else {
                        Logger.verbose("Checking posts...");
                        String var70 = var2.getString("parent_id").replace("t1_", "");

                        for (int var52 = 0; var52 < var3.size(); var52++) {
                            if (((ContentValues) var3.get(var52)).getAsString("_id").equals(var70)) {
                                int var11 = ((ContentValues) var3.get(var52)).getAsInteger("level") + 1;
                                Logger.verbose("Parent level: " + var11);
                                var26.put("level", var11);
                            }
                        }

                        if (!var26.containsKey("level")) {
                            var26.put("level", var4);
                        }
                    }
                }

                String var168 = "subreddit";
                String var27 = var2.optString("subreddit");
                var26.put("subreddit", var27);
                var26.put("subreddit_id", var2.optString("subreddit_id"));
                var26.put("author", PostValuehelper.sanitize(var2.optString("author")));
                var26.put("post_author_fullname", PostValuehelper.sanitize(var2.optString("author_fullname")));
                var26.put("timestamp", TimestampHelper.getRelativeTime(var2.optLong("created_utc")));
                var26.put("post_created_utc", var2.optLong("created_utc"));
                var26.put("edited_timestamp", TimestampHelper.getRelativeTime(var2.optLong("edited")));
                Logger.verbose("EDITED: " + TimestampHelper.getRelativeTime(var2.optLong("edited")));
                JSONObject var107 = var2.optJSONObject("sr_detail");
                if (var107 != null) {
                    String var72 = var107.getString("key_color");
                    if (!com.resync.app.util.TextUtils.isEmpty(var72)) {
                        SubredditColorManager.getInstance().setColor(var27, Color.parseColor(var72));
                    }

                    String var108 = var107.optString("icon_img");
                    if (!TextUtils.isEmpty(var27) && !TextUtils.isEmpty(var108) && !StringUtils.equalsIgnoreCase("null", var108)) {
                        SubredditIconManager.getInstance().setIcon(var27, var108);
                        Logger.verbose("Saved icon: " + var27 + " " + var108);
                    }
                }

                JSONObject var74 = var2.optJSONObject("secure_media");
                if (var74 != null) {
                    JSONObject var75 = var74.optJSONObject("oembed");
                    if (var75 != null) {
                        String var109 = var75.optString("html");
                        String var192 = var75.optString("author_name");
                        String var23 = var75.optString("author_url");
                        Logger.verbose("OEMBED: " + var109);
                        Logger.verbose("OEMBED: " + var192);
                        Logger.verbose("OEMBED: " + var23);
                        if (StringUtils.isNoneEmpty(var109, var192, var23)) {
                            String var79 = var109;
                            if (var109.contains("&lt;/p&gt;&amp;mdash;")) {
                                var79 = var109.split("&lt;/p&gt;&amp;mdash;")[0];
                            }

                            var26.put("embedded", WebsafeUtils.unescapeHtml(var79).trim());
                            var26.put("embedded_author", var192);
                            var26.put("embedded_author_handle", var23.replace("https://twitter.com/", "@"));
                        }
                    }
                }

                var26.put("permalink", PostValuehelper.sanitize(var2.optString("permalink")));
                if (var2.has("title")) {
                    var26.put("title", TitleParserHelper.parseTitle(var2.getString("title")));
                }

                if (var2.has("link_title")) {
                    var26.put("title", Html.fromHtml(var2.getString("link_title")).toString().trim());
                }

                var26.put("distinguished", PostValuehelper.sanitize(var2.optString("distinguished")));
                var26.put("approved_by", PostValuehelper.sanitize(var2.optString("approved_by")));
                var26.put("banned_by", PostValuehelper.sanitize(var2.optString("banned_by")));
                var26.put("link_flair_css_class", PostValuehelper.sanitize(var2.optString("link_flair_text")));
                var26.put("link_flair_background_color", PostValuehelper.sanitize(var2.optString("link_flair_background_color")));
                var26.put("link_flair_id", PostValuehelper.sanitize(var2.optString("link_flair_template_id")));
                JSONArray var80 = var2.optJSONArray("link_flair_richtext");
                if (var80 != null && var80.length() > 0) {
                    ArrayList var110 = new ArrayList();

                    for (int var53 = 0; var53 < var80.length(); var53++) {
                        JSONObject var193 = var80.optJSONObject(var53);
                        if (var193 != null) {
                            String var24 = var193.optString("u");
                            if (StringUtils.isNotEmpty(var24)) {
                                var110.add(var24);
                            }

                            String var194 = var193.optString("t");
                            if (StringUtils.isNotEmpty(var194)) {
                                var110.add(WebsafeUtils.unescapeHtml(var194).trim());
                            }
                        }
                    }

                    if (var110.size() > 0) {
                        Logger.verbose("LINK FLAIR: " + StringUtils.join(var110, ","));
                        var26.put("link_flair_text", StringUtils.join(var110, ","));
                    }
                } else {
                    var26.put("link_flair_text", WebsafeUtils.unescapeHtml(PostValuehelper.sanitize(var2.optString("link_flair_text"))));
                }

                String var195 = "";
                JSONArray var82 = var2.optJSONArray("author_flair_richtext");
                if (var82 != null && var82.length() > 0) {
                    ArrayList var111 = new ArrayList();

                    for (int var54 = 0; var54 < var82.length(); var54++) {
                        JSONObject var208 = var82.optJSONObject(var54);
                        if (var208 != null) {
                            String var28 = var208.optString("u");
                            if (StringUtils.isNotEmpty(var28)) {
                                var111.add(var28);
                            }

                            String var209 = var208.optString("t");
                            if (StringUtils.isNotEmpty(var209)) {
                                var111.add(WebsafeUtils.unescapeHtml(var209).trim());
                            }
                        }
                    }

                    if (var111.size() > 0) {
                        var26.put("author_flair_richtext_u", StringUtils.join(var111, ",,"));
                    }
                } else {
                    var26.put("author_flair_richtext_u", WebsafeUtils.unescapeHtml(PostValuehelper.sanitize(var2.optString("author_flair_text"))));
                }

                if (StringUtils.isNotEmpty(var2.optString("profile_img"))) {
                    var26.put("post_author_profile_pic", WebsafeUtils.unescapeHtml(var2.optString("profile_img")));
                }

                label765:
                {
                    var26.put("domain", PostValuehelper.getDomain(var2.optString("domain")));
                    var26.put("domain_raw", PostValuehelper.getRawDomain(var2.optString("domain")));
                    var26.put("suggested_sort", PostValuehelper.sanitize(var2.optString("suggested_sort")));
                    JSONArray var83 = var2.optJSONArray("crosspost_parent_list");
                    if (var83 != null) {
                        JSONObject var84 = var83.optJSONObject(0);
                        if (var84 != null) {
                            var26.put("post_cross_link", var84.optString("permalink"));
                            var26.put("post_cross_origin", var84.optString("subreddit"));
                            var14 = var84.optBoolean("is_gallery");
                            break label765;
                        }
                    }

                    var14 = false;
                }

                double var8 = var2.optDouble("upvote_ratio", 0.0);
                String var112 = null;
                if (var8 > 0.0) {
                    var10 = (int) (var8 * 100.0);
                    var112 = var10 + "%";
                }

                String var87;
                if (var2.has("user_reports")) {
                    ArrayList var230 = new ArrayList();
                    JSONArray var210 = var2.getJSONArray("user_reports");
                    var10 = 0;
                    String var86 = var19;

                    for (JSONArray var146 = var210; var10 < var146.length(); var10++) {
                        JSONArray var29 = var146.getJSONArray(var10);
                        var230.add(var29.getString(1) + ": " + var29.getString(0));
                    }

                    boolean var15 = var14;
                    var19 = var86;
                    var14 = var15;
                    var87 = var19;
                    if (var230.size() > 0) {
                        var26.put("user_reports", StringUtils.join(var230, ","));
                        var14 = var15;
                        var87 = var19;
                    }
                } else {
                    var87 = "url";
                }

                var26.put("upvote_ratio", var112);
                var26.put("contest_mode", PostValuehelper.boolToInt(var2.optBoolean("contest_mode")));
                var26.put("locked", PostValuehelper.boolToInt(var2.optBoolean("locked")));
                var26.put("stickied", PostValuehelper.boolToInt(var2.optBoolean("stickied")));
                var26.put("saved", PostValuehelper.boolToInt(var2.optBoolean("saved")));
                var26.put("archived", PostValuehelper.boolToInt(var2.optBoolean("archived")));
                var26.put("visited", PostValuehelper.boolToInt(var2.optBoolean("visited")));
                var26.put("hidden", PostValuehelper.boolToInt(var2.optBoolean("hidden")));
                boolean var16 = var2.optBoolean("spoiler");
                boolean var68 = var16;
                if (!var16) {
                    var68 = StringUtils.containsIgnoreCase(var2.optString("link_flair_text"), "spoiler");
                }

                var26.put("spoiler", var68);
                var26.put("author_cakeday", PostValuehelper.boolToInt(var2.optBoolean("author_cakeday")));
                var26.put("is_original_content", PostValuehelper.boolToInt(var2.optBoolean("is_original_content")));
                var26.put("send_replies", PostValuehelper.boolToInt(var2.optBoolean("send_replies")));
                var26.put("new_comment", 1);
                if (var1 == 1 || var1 == 3) {
                    Logger.verbose("Moderated: " + ModeratedSubredditManager.getInstance().getModerated().size());
                    var26.put("mod", PostValuehelper.boolToInt(ModeratedSubredditManager.getInstance().isModerated(var2.getString("subreddit"))));
                }

                var10 = PostValuehelper.boolToInt(var2.optBoolean("over_18"));
                var26.put("over_18", var10);
                var26.put("edited", PostValuehelper.intToFlag(var2.optInt("edited", 0)));
                var26.put("score_hidden", PostValuehelper.boolToInt(var2.optBoolean("score_hidden")));
                var26.put("childCount", 0);
                var26.put("collapsed", 0);
                if (var1 == 9) {
                    JSONArray var148 = var2.getJSONArray("children");
                    int var12 = var148.length();
                    String[] var114 = new String[var12];

                    for (int var58 = 0; var58 < var148.length(); var58++) {
                        var114[var58] = var148.getString(var58);
                    }

                    var26.put("commentMoreIds", TextUtils.join(",", var114));
                    var26.put("commentMoreCount", var12);
                    if (var2.getString("id").equals("_")) {
                        var26.put("_id", "more_" + var2.getString("parent_id"));
                    } else {
                        var26.put("_id", "more_" + var2.getString("id"));
                    }
                }

                int var13 = SyncTypeHelper.resolveSyncType(var2, var1);
                if (var1 == 3) {
                    var26.put("thumbnail", PostThumbnailHelper.getThumbnail(var2));
                }

                AbstractRedditPreview var231 = PostPreviewHelper.buildPreview(var2, var13, WindowUtils.getDisplayWidth(), WindowUtils.getDisplayHeight());
                String var117 = var2.optString(var87);
                if (!TextUtils.isEmpty(var117)) {
                    var26.put("raw_url", WebsafeUtils.unescapeHtml(var117));
                }

                String var126;
                label734:
                {
                    if (var14) {
                        JSONObject var149 = var2.optJSONArray("crosspost_parent_list").optJSONObject(0);
                        var26.put("post_cross_link", var149.optString("permalink"));
                        var26.put("post_cross_origin", var149.optString("subreddit"));
                        String var118 = var149.optString("subreddit");
                        var168 = var149.getString("id");
                        String var119 = "https://www.reddit.com/r/" + var118 + "/comments/" + var168 + "?is_gallery=true";
                        var26.put("sync_type", 6);
                        var26.put(var87, var119);
                    } else {
                        if (!var2.optBoolean("is_gallery")) {
                            if (var231 instanceof RedditImagePreview || var231 instanceof RedditAlbumPreview || var231 instanceof RedditLinkPreview) {
                                int var63;
                                var63 = var10;
                                var21 = "link_flair_text";
                                var195 = "subreddit";
                                var26.put("sync_type", var231.type);
                                String var138 = var231.url;
                                var168 = var87;
                                var26.put(var168, var138);
                                label592:
                                if (StringUtils.isNotEmpty(var231.url)) {
                                    label876:
                                    {
                                        label599:
                                        try {
                                            if (!var231.url.startsWith("/u/") && !var231.url.startsWith("/r/")) {
                                                break label599;
                                            }
                                            break label876;
                                        } catch (Exception var33) {
                                            Logger.logException(var33);
                                            break label592;
                                        }

                                        try {
                                            URL var100 = new URL(var231.url.replace("www.", ""));
                                            String var139 = var100.getHost();
                                            var26.put("post_fav_icon", "https://" + var139 + "/favicon.ico");
                                        } catch (Exception var31) {
                                            Logger.logException(var31);
                                        }
                                        break label592;
                                    }

                                    try {
                                        var26.put("post_fav_icon", "https://www.redditstatic.com/desktop2x/img/favicon/favicon-32x32.png");
                                    } catch (Exception var30) {
                                        Logger.logException(var30);
                                    }
                                }

                                String var102 = WebsafeUtils.unescapeHtml(var2.getString("selftext"));
                                String var140 = RedditHelper.processMarkdownToHtml(var27, var102);
                                var26.put("selftext_raw", var102);
                                var26.put("selftext_processed", var140);
                                ImagePreview var205 = var231.image;
                                var87 = var168;
                                var19 = var21;
                                var126 = var195;
                                var10 = var63;
                                if (var205 != null) {
                                    var26.put("preview_url", var205.url);
                                    var26.put("preview_width", var231.image.width);
                                    var26.put("preview_height", var231.image.height);
                                    var26.put("preview_infographic", var231.tallImage);
                                    var26.put("preview_duration", var231.videoDuration);
                                    var26.put("video_preview", var231.videoUrl);
                                    String var141 = var231.url;
                                    String var103 = var141;
                                    if (var141.contains("imgur")) {
                                        var103 = var141.replace(".gifv", ".mp4").replace(".gif", ".mp4");
                                    }

                                    boolean var66;
                                    if ((UiSingleton.getInstance().getUiMode() == 4 || UiSingleton.getInstance().getUiMode() == 5)
                                        && SettingsSingleton.shouldPreload(1)) {
                                        if (StringUtils.equalsIgnoreCase(var231.image.url, var103)) {
                                            Logger.debug("PostParserHelper", "Skipping caching preview: " + var103);
                                        } else {
                                            Logger.verbose("PostParserHelper", "Caching preview: " + var103);
                                            GlideHelper.preload(var231.image.url);
                                        }

                                        var66 = true;
                                    } else {
                                        Logger.debug("PostParserHelper", "Not caching preview");
                                        var66 = false;
                                    }

                                    if (var103.endsWith(".mp4")) {
                                        if (SettingsSingleton.shouldPreload(var0, 3)) {
                                            Logger.verbose("PostParserHelper", "Skipping caching GIF!");
                                        } else {
                                            Logger.verbose("PostParserHelper", "Not caching GIF");
                                        }
                                    } else if (LinkTypeHelper.isRedditVideoHls(var103)) {
                                        Logger.verbose("PostParserHelper", "Not caching hls playlist: " + var103);
                                    } else if (LinkTypeHelper.isImage(var103)
                                        && !LinkTypeHelper.isGfycat(var103)
                                        && !LinkTypeHelper.isRedgifs(var103)
                                        && !LinkTypeHelper.isVidme(var103)
                                        && !LinkTypeHelper.isDeviantArt(var103)
                                        && !LinkTypeHelper.isImgflip(var103)
                                        && !LinkTypeHelper.isSliMg(var103)
                                        && !LinkTypeHelper.isMixtape(var103)) {
                                        if (SettingsSingleton.shouldPreload(var0, 0)) {
                                            Logger.verbose("PostParserHelper", "Caching image");
                                            GlideHelper.preload(var103);
                                        } else {
                                            Logger.verbose("PostParserHelper", "Not caching image");
                                        }
                                    }

                                    if (UiSingleton.getInstance().getUiMode() != 4 && UiSingleton.getInstance().getUiMode() != 5) {
                                        String var202 = var26.getAsString("thumbnail");
                                        var87 = var168;
                                        var19 = var21;
                                        var126 = var195;
                                        var10 = var63;
                                        if (LinkTypeHelper.isValidUrl(var202)) {
                                            if (SettingsSingleton.shouldPreload(var0, 2)) {
                                                Logger.verbose("PostParserHelper", "Other UI caching thumbnail");
                                                GlideHelper.preload(var202);
                                                var87 = var168;
                                                var19 = var21;
                                                var126 = var195;
                                                var10 = var63;
                                            } else {
                                                Logger.verbose("PostParserHelper", "Other UI not caching thumbnail");
                                                var87 = var168;
                                                var19 = var21;
                                                var126 = var195;
                                                var10 = var63;
                                            }
                                        }
                                    } else {
                                        var87 = var168;
                                        var19 = var21;
                                        var126 = var195;
                                        var10 = var63;
                                        if (!var66) {
                                            String var202 = var26.getAsString("thumbnail");
                                            var87 = var168;
                                            var19 = var21;
                                            var126 = var195;
                                            var10 = var63;
                                            if (LinkTypeHelper.isValidUrl(var202)) {
                                                if (SettingsSingleton.shouldPreload(var0, 2)) {
                                                    Logger.verbose("PostParserHelper", "Cards UI caching thumbnail");
                                                    GlideHelper.preload(var202);
                                                    var87 = var168;
                                                    var19 = var21;
                                                    var126 = var195;
                                                    var10 = var63;
                                                } else {
                                                    Logger.verbose("PostParserHelper", "Cards UI not caching thumbnail");
                                                    var10 = var63;
                                                    var126 = var195;
                                                    var19 = var21;
                                                    var87 = var168;
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (var13 == 2) {
                                String var127 = WebsafeUtils.unescapeHtml(var2.getString("selftext"));
                                Matcher var156 = Patterns.WEB_URL.matcher(var127);
                                ArrayList var182 = new ArrayList();

                                while (var156.find()) {
                                    String var212 = var156.group();
                                    if (StringUtils.contains(var212, "https://preview.redd.it")) {
                                        var182.add(var212);
                                    }
                                }

                                Iterator var213 = var182.iterator();
                                var21 = var195;
                                var195 = var127;
                                String var128 = "link_flair_text";
                                var19 = var87;
                                String var88 = var168;

                                while (true) {
                                    while (true) {
                                        if (!var213.hasNext()) {
                                            var168 = var19;
                                            var19 = var128;
                                            String var202 = var88;
                                            String var89 = RedditHelper.processMarkdownToHtml(var27, var195);
                                            var26.put("sync_type", 2);
                                            var26.put("selftext_raw", var195);
                                            var26.put("selftext_processed", var89);
                                            // Literal replace: a $ in the replacement must not be
                                            // read as a regex group reference.
                                            String var129 = var89.replace("&#x200B;", var21);
                                            String var90 = var129;
                                            if (var129 != null) {
                                                var90 = StringUtils.trimToNull(var129);
                                            }

                                            String var130 = var90;
                                            if (!TextUtils.isEmpty(var90)) {
                                                var130 = var90;
                                                if (var90.contains("<table>")) {
                                                    var130 = null;
                                                }
                                            }

                                            String var91 = var130;
                                            if (var68) {
                                                var91 = "&lt;Spoiler hidden&gt;";
                                            }

                                            String var131 = var91;
                                            if (var91 != null) {
                                                String[] var197 = var91.split("\n.");
                                                boolean var60;
                                                if (var197.length > 1) {
                                                    var91 = var197[0].trim();
                                                    var60 = true;
                                                } else {
                                                    var60 = false;
                                                }

                                                SpannableStringBuilder var92 = (SpannableStringBuilder) Html.fromHtml(var91);
                                                var92.clearSpans();
                                                String var93 = var92.toString().replace("￼", var21).trim();
                                                var131 = var93;
                                                if (var60) {
                                                    var131 = var93 + "\n…";
                                                }

                                                if (var197[0].trim().contains("<abbr>")) {
                                                    var131 = "<Spoiler hidden>";
                                                }
                                            }

                                            var26.put("selftext_processed_preview", StringUtils.trimToNull(StringUtils.removeAll(var131, "​")));
                                            if (var231 != null) {
                                                ImagePreview var94 = var231.image;
                                                if (var94 != null) {
                                                    var26.put("preview_url", var94.url);
                                                    var26.put("preview_width", var231.image.width);
                                                    var26.put("preview_height", var231.image.height);
                                                    if ((UiSingleton.getInstance().getUiMode() == 4 || UiSingleton.getInstance().getUiMode() == 5)
                                                        && SettingsSingleton.shouldPreload(var0, 1)) {
                                                        Logger.verbose("PostParserHelper", "Caching preview: " + var231.image.url);
                                                        GlideHelper.preload(var231.image.url);
                                                        var126 = var202;
                                                        var87 = var168;
                                                    } else {
                                                        Logger.debug("PostParserHelper", "Not caching preview");
                                                        var126 = var202;
                                                        var87 = var168;
                                                    }
                                                    break label734;
                                                }
                                            }

                                            var126 = var202;
                                            var87 = var168;
                                            break label734;
                                        }

                                        var168 = (String) var213.next();
                                        if (!var168.contains("height")) {
                                            label841:
                                            {
                                                JSONObject var236;
                                                try {
                                                    var25 = var168.replace("https://preview.redd.it/", var21).split("\\.")[0];
                                                    var236 = var2.optJSONObject("media_metadata");
                                                } catch (Exception var43) {
                                                    var172 = var43;
                                                    break label841;
                                                }

                                                if (var236 == null) {
                                                    continue;
                                                }

                                                JSONObject var222;
                                                try {
                                                    if (!var236.has(var25)) {
                                                        continue;
                                                    }

                                                    var222 = var236.getJSONObject(var25);
                                                } catch (Exception var42) {
                                                    var172 = var42;
                                                    break label841;
                                                }

                                                String var238;
                                                try {
                                                    var238 = "Obj:  " + var222;
                                                } catch (Exception var41) {
                                                    var172 = var41;
                                                    break label841;
                                                }

                                                JSONObject var223;
                                                try {
                                                    Logger.verbose(var88, var238);
                                                    var223 = var222.optJSONObject("s");
                                                } catch (Exception var40) {
                                                    var172 = var40;
                                                    break label841;
                                                }

                                                if (var223 == null) {
                                                    continue;
                                                }

                                                int var64;
                                                try {
                                                    var64 = var223.getInt("x");
                                                } catch (Exception var39) {
                                                    var172 = var39;
                                                    break label841;
                                                }

                                                int var59;
                                                try {
                                                    var59 = var223.getInt("y");
                                                } catch (Exception var38) {
                                                    var172 = var38;
                                                    break label841;
                                                }

                                                try {
                                                    Logger.verbose("FOUND: " + var168);
                                                    Logger.verbose("FOUND: " + var64 + " " + var59);
                                                    var168 = var195.replace(var168, var168 + "&height=" + var59);
                                                    break;
                                                } catch (Exception var37) {
                                                    var172 = var37;
                                                }
                                            }

                                            Logger.logException(var172);
                                        }
                                    }

                                    var195 = var168;
                                }
                            } else {
                                var168 = var87;
                                var195 = "link_flair_text";
                                if (var13 == 11) {
                                    String var96 = WebsafeUtils.unescapeHtml(var2.getString("body"));
                                    JSONObject var158 = var2.optJSONObject("media_metadata");
                                    String var133 = var96;
                                    if (var158 != null) {
                                        ArrayList var214 = new ArrayList<>(IteratorUtils.toList(var158.keys()));
                                        int var61 = 0;

                                        while (true) {
                                            var133 = var96;
                                            if (var61 >= var214.size()) {
                                                break;
                                            }

                                            JSONObject var232 = var158.getJSONObject((String) var214.get(var61));
                                            var25 = var232.optString(var21);
                                            Logger.verbose("Found emote: " + var25);
                                            String var135 = var96;
                                            if (StringUtils.isNotEmpty(var25)) {
                                                var135 = var96;
                                                if (StringUtils.startsWithIgnoreCase(var25, "emote|")) {
                                                    JSONObject var233 = var232.optJSONObject("s");
                                                    var135 = var96;
                                                    if (var233 != null) {
                                                        if (var233.has("u")) {
                                                            String var234 = WebsafeUtils.unescapeHtml(var233.getString("u"));
                                                            Logger.verbose("found emote: " + var214.get(var61) + " " + var234);
                                                            var135 = var96.replaceAll(Pattern.quote(var25), var234);
                                                        } else {
                                                            var135 = var96;
                                                            if (var233.has("gif")) {
                                                                String var137 = WebsafeUtils.unescapeHtml(var233.getString("gif"));
                                                                Logger.verbose("found emote: " + var214.get(var61) + " " + var137);
                                                                var135 = var96.replaceAll(Pattern.quote(var25), var137);
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            var61++;
                                            var96 = var135;
                                        }
                                    }

                                    Matcher var159 = Pattern.compile("\\[gif\\]\\(giphy\\|(.*?)\\)", 32).matcher(var133);

                                    while (true) {
                                        JSONObject var219;
                                        String var97;
                                        while (true) {
                                            while (true) {
                                                if (!var159.find()) {
                                                    String var99 = RedditHelper.processMarkdownToHtml(var27, var133);
                                                    var26.put("sync_type", 11);
                                                    var26.put("body_raw", var133);
                                                    var26.put("body_processed", var99);
                                                    var19 = var195;
                                                    var126 = "subreddit";
                                                    var87 = var168;
                                                    break label734;
                                                }

                                                try {
                                                    var97 = var159.group(0);
                                                    String var215 = var159.group(1);
                                                    Logger.verbose("html", "Found giphy ID:  " + var215);
                                                    var21 = "giphy|" + var159.group(1);
                                                    Logger.verbose("html", "Found giphy key:  " + var215);
                                                    Logger.verbose("html", "Found giphy:  " + var97);
                                                    var219 = var2.optJSONObject("media_metadata");
                                                    break;
                                                } catch (IndexOutOfBoundsException var34) {
                                                    Logger.logException(var34);
                                                }
                                            }

                                            if (var219 != null) {
                                                try {
                                                    if (var219.has(var21)) {
                                                        JSONObject var229 = var219.getJSONObject(var21);
                                                        Logger.verbose("html", "Giphy obj:  " + var229);
                                                        var219 = var229.optJSONObject("s");
                                                        break;
                                                    }
                                                } catch (IndexOutOfBoundsException var35) {
                                                    Logger.logException(var35);
                                                }
                                            }
                                        }

                                        if (var219 != null) {
                                            String var98;
                                            try {
                                                int var62 = var219.getInt("x");
                                                int var65 = var219.getInt("y");
                                                String var220 = var21.replace("|downsized", "");
                                                var98 = var133.replace(var97, "[gif](giphy|" + var220 + "|" + var62 + "|" + var65 + ")");
                                            } catch (IndexOutOfBoundsException var36) {
                                                Logger.logException(var36);
                                                continue;
                                            }

                                            var133 = var98;
                                        }
                                    }
                                } else {
                                    var26.put("sync_type", var13);
                                    var87 = var168;
                                    var126 = "subreddit";
                                    var19 = var195;
                                }
                            }
                            break label734;
                        }

                        var19 = var2.optString("subreddit");
                        String var120 = var2.getString("id");
                        String var121 = "https://www.reddit.com/r/" + var19 + "/comments/" + var120 + "?is_gallery=true";
                        var26.put("sync_type", 6);
                        var26.put(var87, var121);

                        label849:
                        {
                            JSONObject var152;
                            try {
                                var107 = var2.optJSONObject("gallery_data");
                                var152 = var2.optJSONObject("media_metadata");
                            } catch (Exception var45) {
                                Logger.logException(var45);
                                break label849;
                            }

                            label716:
                            if (var107 != null && var152 != null) {
                                String var124;
                                label726:
                                {
                                    try {
                                        JSONArray var123 = var152.getJSONObject(var107.optJSONArray("items").getJSONObject(0).getString("media_id")).getJSONArray("p");
                                        JSONObject var153 = var123.getJSONObject(var123.length() - 1);
                                        var124 = WebsafeUtils.unescapeHtml(var153.getString("u"));
                                        var26.put("preview_url", var124);
                                        var26.put("preview_width", var153.getInt("x"));
                                        var26.put("preview_height", var153.getInt("y"));
                                        var26.put("sync_type", 10);
                                        if (UiSingleton.getInstance().getUiMode() != 4 && UiSingleton.getInstance().getUiMode() != 5) {
                                            break label726;
                                        }
                                    } catch (Exception var46) {
                                        Logger.logException(var46);
                                        break label716;
                                    }

                                    try {
                                        if (SettingsSingleton.shouldPreload(1)) {
                                            GlideHelper.preload(var124);
                                            break label716;
                                        }
                                    } catch (Exception var44) {
                                        Logger.logException(var44);
                                        break label716;
                                    }
                                }

                                try {
                                    Logger.debug("PostParserHelper", "Not caching preview");
                                } catch (Exception var32) {
                                    Logger.logException(var32);
                                }
                            }
                        }

                        String var125 = WebsafeUtils.unescapeHtml(var2.getString("selftext"));
                        var19 = RedditHelper.processMarkdownToHtml(var27, var125);
                        var26.put("selftext_raw", var125);
                        var26.put("selftext_processed", var19);
                    }

                    var126 = "subreddit";
                    var19 = "link_flair_text";
                }

                if (var6) {
                    var21 = var26.getAsString("author");
                    Iterator var177 = FilterHelper.getInstance().getFilterList(3).iterator();

                    while (var177.hasNext()) {
                        if (((String) var177.next()).equalsIgnoreCase(var21)) {
                            Logger.verbose("User filter excluded a comment!");
                            var26.put("comment_filtered", "1");
                        }
                    }
                }

                if (!var6) {
                    var168 = WebsafeUtils.unescapeHtml(PostValuehelper.sanitize(var2.optString(var19)));
                    var195 = WebsafeUtils.unescapeHtml(PostValuehelper.sanitize(var2.optString("author_flair_text")));

                    for (String var160 : FilterHelper.getInstance().getFilterList(4)) {
                        if (var160.equalsIgnoreCase(var168) || var160.equalsIgnoreCase(var195)) {
                            Logger.verbose("Flair filter excluded a post!");
                            return;
                        }
                    }

                    var19 = var26.getAsString("domain");
                    Iterator var179 = FilterHelper.getInstance().getFilterList(1).iterator();

                    while (var179.hasNext()) {
                        if (((String) var179.next()).equalsIgnoreCase(var19)) {
                            Logger.verbose("Domain filter excluded a post!");
                            return;
                        }
                    }

                    var21 = var26.getAsString("title");

                    for (String var162 : FilterHelper.getInstance().getFilterList(0)) {
                        if (!TextUtils.isEmpty(var21)) {
                            Locale var201 = Locale.ENGLISH;
                            if (var21.toLowerCase(var201).contains(var162.toLowerCase(var201))) {
                                Logger.verbose("Keyword filter excluded a post: " + var162);
                                return;
                            }
                        }
                    }

                    var19 = var26.getAsString("author");
                    Iterator var181 = FilterHelper.getInstance().getFilterList(3).iterator();

                    while (var181.hasNext()) {
                        if (((String) var181.next()).equalsIgnoreCase(var19)) {
                            Logger.verbose("User filter excluded a post!");
                            return;
                        }
                    }

                    String var145 = var26.getAsString(var126);
                    if (!TextUtils.isEmpty(var145)
                        && !TextUtils.isEmpty(var7)
                        && (var7.equalsIgnoreCase("all") || var7.equalsIgnoreCase("frontpage") || var7.equalsIgnoreCase("popular"))) {
                        Iterator var164 = FilterHelper.getInstance().getFilterList(2).iterator();

                        while (var164.hasNext()) {
                            if (((String) var164.next()).equalsIgnoreCase(var145)) {
                                Logger.verbose("Subreddit filter excluded a post!");
                                return;
                            }
                        }
                    }

                    if (var1 == 3 && !SettingsSingleton.getSettings().filtersContentLink && var13 == 3) {
                        Logger.verbose("Content filter excluded a link post!");
                        return;
                    }

                    if (var1 == 3 && !SettingsSingleton.getSettings().filtersContentImage && var13 == 1) {
                        Logger.verbose("Content filter excluded an image post!");
                        return;
                    }

                    if (var1 == 3 && !SettingsSingleton.getSettings().filtersContentGif && var13 == 7) {
                        Logger.verbose("Content filter excluded a gif post!");
                        return;
                    }

                    if (var1 == 3 && !SettingsSingleton.getSettings().filtersContentAlbum && var13 == 6) {
                        Logger.verbose("Content filter excluded an album post!");
                        return;
                    }

                    if (var1 == 3 && !SettingsSingleton.getSettings().filtersContentVideo && var13 == 4) {
                        Logger.verbose("Content filter excluded a video post!");
                        return;
                    }

                    if (var1 == 3 && !SettingsSingleton.getSettings().filtersContentVideo && LinkTypeHelper.isYouTube(var26.getAsString(var87))) {
                        Logger.verbose("Content filter excluded a video post!");
                        return;
                    }

                    if (var1 == 3 && !SettingsSingleton.getSettings().filtersContentText && var13 == 2) {
                        Logger.verbose("Content filter excluded a text post!");
                        return;
                    }

                    if (var1 == 3 && !SettingsSingleton.getSettings().filtersContentText && var13 == 5) {
                        Logger.verbose("Content filter excluded a text post!");
                        return;
                    }

                    if (var10 == 1 && !SettingsSingleton.getSettings().filtersContentNsfw) {
                        Logger.verbose("Content filter excluded a NSFW post");
                        return;
                    }
                }

                var3.add(var26);
                if (var1 == 1) {
                    var2 = var2.optJSONObject("replies");
                    if (var2 != null) {
                        JSONArray var50 = var2.getJSONObject("data").getJSONArray("children");

                        for (int var48 = 0; var48 < var50.length(); var48++) {
                            JSONObject var104 = var50.getJSONObject(var48);
                            if (var104.getString("kind").equals("t1")) {
                                parsePost(var0, 1, var104.getJSONObject("data"), var3, var4 + 1, var5, var6, var7);
                            }

                            if (var104.getString("kind").equals("more")) {
                                parsePost(var0, 9, var104.getJSONObject("data"), var3, var4 + 1, var5, var6, var7);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void parsePostDefaults(Context context, int kind, JSONObject jSONObject, ArrayList<ContentValues> arrayList, boolean isComment, String currentSubreddit) throws JSONException {
        parsePost(context, kind, jSONObject, arrayList, 0, false, isComment, currentSubreddit);
    }
}
