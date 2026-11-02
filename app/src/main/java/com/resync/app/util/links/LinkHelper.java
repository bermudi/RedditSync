package com.resync.app.util.links;

import android.content.Context;
import android.view.View;
import androidx.core.util.Pair;
import com.resync.app.helpers.ui.StaticToast;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import com.resync.app.media.imgur.ImgurImageHelper;
import com.resync.app.util.Logger;
import com.resync.app.util.TextUtils;
import com.resync.app.model.Post;
import com.resync.app.util.links.LinkHandler;

/* JADX INFO: renamed from: y7.b */
/* JADX INFO: compiled from: LinkHelper.java */
/* JADX INFO: loaded from: classes.dex */
public abstract class LinkHelper {
    private static String extractUrl(Post post) {
        if (post.getSyncType() == 2) {
            if (StringUtils.isNotEmpty(post.getPreviewUrl())) {
                return post.getPreviewUrl();
            }
            if (StringUtils.isNotEmpty(post.getThumbnail())) {
                return post.getThumbnail();
            }
        }
        return post.getUrl();
    }

    public static boolean open(Context context, String url) {
        return open(context, null, null, url, null, null, null, false, false, false);
    }

    public static boolean open(Context context, Pair<View, String>[] pairArr, String subreddit, String url, String previewUrl, String title, String permalink, boolean visited, boolean blurNsfw, boolean infographic) {
        String workingUrl = url;
        Logger.verbose("LinkHelper", "Opening link: " + url);
        Logger.verbose("LinkHelper", "Link title: " + title);
        Logger.verbose("LinkHelper", "Is infographic: " + infographic);
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        // Reddit's /s/ share links: resolve to the post they point at before anything else
        // touches them (nothing below knows their shape).
        if (SLinkResolver.handle(context, url)) {
            return true;
        }
        if (url.contains("reddit.com/link/") && url.contains("/video/")) {
            StaticToast.debugToast("Opening strange reddit video link");
            LinkHandler.openCustomTab(context, url);
            return true;
        }
        if (url.contains("reddit.com/poll/")) {
            Logger.verbose("LinkHelper", "Was poll");
            LinkHandler.openCustomTab(context, url);
            return true;
        }
        if (url.startsWith("https://www.google.com/amp/s/amp.reddit.com")) {
            Logger.verbose("LinkHelper", "Was AMP");
            open(context, url.replace("https://www.google.com/amp/s/amp.reddit.com", "https://reddit.com"));
            return true;
        }
        if (url.endsWith("?utm_source=amp&utm_medium=post_header")) {
            Logger.verbose("LinkHelper", "Remove source crap");
            open(context, url.replace("?utm_source=amp&utm_medium=post_header", ""));
            return true;
        }
        if (url.endsWith("?utm_source=amp")) {
            Logger.verbose("LinkHelper", "Remove AMP crap");
            open(context, url.replace("?utm_source=amp", ""));
            return true;
        }
        if (url.contains("reddit.com/contact") || url.contains("reddit.com/prefs") || url.contains("reddit.com/coins")) {
            Logger.verbose("LinkHelper", "Was interal link");
            LinkHandler.openExternalOrInternal(context, url);
            return true;
        }
        if (LinkTypeHelper.isAgeVerification(url)) {
            Logger.verbose("LinkHelper", "Was age verification");
            LinkHandler.openAgeVerification(context, url);
            return true;
        }
        if (url.contains("old.reddit.com")) {
            Logger.verbose("LinkHelper", "Was old");
            open(context, url.replace("old.reddit.com", "reddit.com"));
            return true;
        }
        if ("http://imgur.com/gallery/xIolo".equals(url) || "https://imgur.com/gallery/xIolo".equals(url)) {
            workingUrl = "http://i.imgur.com/xIolo.gif";
        }
        Locale locale = Locale.ENGLISH;
        if (workingUrl.toLowerCase(locale).startsWith("https")) {
            workingUrl = "https" + workingUrl.substring(5, workingUrl.length());
        } else if (workingUrl.toLowerCase(locale).startsWith("http")) {
            workingUrl = "http" + workingUrl.substring(4, workingUrl.length());
        }
        String normalizedUrl = workingUrl;
        if (LinkTypeHelper.isGallery(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was gallery");
            LinkHandler.openAlbum(context, subreddit, normalizedUrl, previewUrl, title, permalink);
            return true;
        }
        if (LinkTypeHelper.isSearch(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was search");
            LinkHandler.openSearch(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isShortened(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was shortened");
            LinkHandler.openShortened(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isDomain(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was domain");
            LinkHandler.openDomain(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isMulti(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was multi");
            LinkHandler.openMulti(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isRickroll(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was rickroll");
            LinkHandler.openRickroll(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isYouTube(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was YouTube");
            LinkHandler.openYouTube(context, pairArr, normalizedUrl, previewUrl);
            return true;
        }
        if (LinkTypeHelper.isWiki(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was wiki");
            LinkHandler.openWiki(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isCompose(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was compose");
            LinkHandler.openCompose(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isDuplicate(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was duplicate");
            LinkHandler.openExternalOrInternal(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isSubreddit(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was subreddit");
            LinkHandler.openSubreddit(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isComments(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was comments");
            LinkHandler.openCommentsLink(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isRedditUser(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was reddit user");
            LinkHandler.openProfile(context, normalizedUrl);
            return true;
        }
        if (LinkTypeHelper.isTumblr(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was tumblr");
            LinkHandler.openTumblr(context, subreddit, normalizedUrl, previewUrl, title, permalink);
            return true;
        }
        if (ImgurImageHelper.isAlbum(normalizedUrl) || ImgurImageHelper.isMultiImage(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was album");
            LinkHandler.openImgurAlbum(context, subreddit, normalizedUrl, previewUrl, title, permalink);
            return true;
        }
        if (LinkTypeHelper.isStreamable(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was Streamable");
            LinkHandler.openStreamable(context, pairArr, subreddit, normalizedUrl, previewUrl, title, permalink, visited, blurNsfw);
            return true;
        }
        if (LinkTypeHelper.isMixtape(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was mixtape");
            LinkHandler.openMixtape(context, pairArr, subreddit, normalizedUrl, previewUrl, title, permalink, visited, blurNsfw);
            return true;
        }
        if (LinkTypeHelper.isVidme(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was vidme");
            LinkHandler.openVidme(context, pairArr, subreddit, normalizedUrl, previewUrl, title, permalink, visited, blurNsfw);
            return true;
        }
        if (LinkTypeHelper.isGfycat(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was gfycat");
            LinkHandler.openGfycat(context, pairArr, subreddit, normalizedUrl, previewUrl, title, permalink, visited, blurNsfw);
            return true;
        }
        if (LinkTypeHelper.isRedgifs(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was redgifs");
            LinkHandler.openRedgifs(context, pairArr, subreddit, normalizedUrl, previewUrl, title, permalink, visited, blurNsfw);
            return true;
        }
        if (LinkTypeHelper.isRedditVideo(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was reddit video");
            LinkHandler.openRedditVideo(context, pairArr, subreddit, normalizedUrl, previewUrl, title, permalink, visited, blurNsfw);
            return true;
        }
        if (LinkTypeHelper.isImage(normalizedUrl)) {
            Logger.verbose("LinkHelper", "Was image");
            LinkHandler.openImage(context, pairArr, subreddit, normalizedUrl, previewUrl, title, permalink, visited, blurNsfw, infographic);
            return true;
        }
        Logger.verbose("LinkHelper", "Was default link");
        LinkHandler.openExternalOrInternal(context, normalizedUrl);
        return true;
    }

    public static boolean open(Context context, Pair<View, String>[] pairArr, Post post) {
        if (post == null) {
            return false;
        }
        Logger.verbose("LinkHelper", "Opening link: " + post);
        boolean visited = post.isVisited();
        boolean blurNsfw = post.shouldBlurNsfw();
        boolean infographic = post.isPreviewInfographic();
        return open(context, pairArr, post.getSubreddit(), extractUrl(post), post.getPreviewUrl(), post.getTitle(), "/r/" + post.getSubreddit() + "/comments/" + post.getId(), visited, blurNsfw, infographic);
    }
}
