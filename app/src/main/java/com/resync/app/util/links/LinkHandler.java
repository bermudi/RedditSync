package com.resync.app.util.links;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import com.resync.app.R;
import com.resync.app.RedditApplication;
import com.resync.app.singleton.SettingsSingleton;
import com.resync.app.ui.activities.BaseActivity;
import com.resync.app.ui.activities.BaseMaterialDragActivity;
import com.resync.app.ui.activities.CasualActivity;
import com.resync.app.ui.activities.CommentsActivity;
import com.resync.app.ui.activities.CustomYouTubeVideoActivity;
import com.resync.app.ui.activities.WebViewActivity;
import com.resync.app.ui.activities.media.AbstractImageActivity;
import com.resync.app.ui.fragment_dialogs.EditFragment;
import com.resync.app.ui.fragment_dialogs.bottom.WikiBottomSheetFragment;
import com.resync.app.ui.fragments.TabbedCommentsFragment;
import com.resync.app.ui.views.images.CustomImageView;
import com.resync.app.helpers.ui.AlertDialogHelper;
import com.resync.app.helpers.ui.MonetUtil;
import com.resync.app.helpers.ui.StaticToast;
import com.resync.app.helpers.ui.TabletHelper;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import com.resync.app.media.images.ImageHelper;
import com.resync.app.dialogs.FragmentUtils;
import com.resync.app.helpers.BypassHelper;
import com.resync.app.helpers.ChromeCustomTabsHelper;
import com.resync.app.helpers.ContextHelper;
import com.resync.app.helpers.CrashlyticsHelper;
import com.resync.app.helpers.PreviousActivityHelper;
import com.resync.app.helpers.WindowUtils;
import com.resync.app.dialogs.RandomProgressDialogBottomSheet;
import com.resync.app.dialogs.RickrollAlertDialogBottomSheet;
import com.resync.app.ui.interfaces.PostHost;
import com.resync.app.helpers.CommentsStateHelper;
import com.resync.app.util.Logger;
import com.resync.app.util.TestUtils;
import com.resync.app.util.TextUtils;
import com.resync.app.model.Post;
import com.resync.app.util.links.LinkHelper;
import com.resync.app.util.links.LinkTypeHelper;

/* JADX INFO: compiled from: LinkHandler.java */
/* JADX INFO: loaded from: classes.dex */
public class LinkHandler {

    static class AlibabaHelpClickListener implements DialogInterface.OnClickListener {

        final /* synthetic */ Context context;

        AlibabaHelpClickListener(Context context) {
            this.context = context;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialogInterface, int which) {
            LinkHandler.openCommentsLink(this.context, "https://www.reddit.com/r/redditsync/comments/5ml2t8/alibabacom_b2b_trade_app_help/");
        }
    }

    public static void openComments(Context context, Post post) {
        openComments(context, null, post);
    }

    public static void openComments(Context context, Pair<View, String>[] pairArr, Post post) {
        if (TabletHelper.isTabletMode() && (context instanceof PostHost) && (context instanceof BaseActivity)) {
            View viewFindViewById = ((BaseActivity) context).findViewById(R.id.second_pane);
            Fragment fragmentJ0 = ContextHelper.getFragmentManager(context).findFragmentByTag("TabbedCommentsFragment");
            if (viewFindViewById != null && viewFindViewById.getVisibility() == 0 && (fragmentJ0 instanceof TabbedCommentsFragment)) {
                ((TabbedCommentsFragment) fragmentJ0).addOrSelectTab(post);
                return;
            }
        }
        if (CommentsStateHelper.hasState(post.getId())) {
            startActivity(context, CommentsActivity.createIntent(context, post.getId(), null, post.getSubreddit(), false));
        } else {
            startActivity(context, CommentsActivity.createPostIntent(context, post));
        }
        if (SettingsSingleton.getSettings().animateTransitionComments) {
            ((Activity) context).overridePendingTransition(R.anim.slide_up_enter, R.anim.slide_up_exit);
        }
    }

    public static void openCommentsLink(Context context, String str) {
        Logger.verbose("Opening comments link: " + str);
        String commentsId = getCommentsId(str);
        String commentId = getCommentsCommentId(str);
        String subreddit = getCommentsSubreddit(str);
        boolean noParticipation = isNoParticipation(str);
        Logger.verbose("Comments", "ID: " + commentsId);
        Logger.verbose("Comments", "Comments ID: " + commentId);
        Logger.verbose("Comments", "Sub: " + subreddit);
        openCommentsLinkResolved(context, commentsId, commentId, subreddit, noParticipation);
    }

    public static void openCommentsLinkResolved(Context context, String str, String str2, String str3, boolean z) {
        if (!TextUtils.isEmpty(str)) {
            startActivity(context, CommentsActivity.createIntent(context, str, str2, str3, z));
            return;
        }
        StaticToast.showWithContext(context, "Invalid comments id: " + str);
    }

    public static void openCommentsForPost(Context context, Post post) {
        startActivity(context, CommentsActivity.createBackgroundIntent(context, post));
    }

    public static void openCompose(Context context, String str) {
        String queryParameter;
        String queryParameter2;
        Logger.verbose("Opening compose link: " + str);
        String queryParameter3 = null;
        try {
            Uri uri = Uri.parse(str);
            queryParameter = uri.getQueryParameter("to");
            try {
                queryParameter2 = uri.getQueryParameter("subject");
                try {
                    queryParameter3 = uri.getQueryParameter("message");
                } catch (Exception unused) {
                }
            } catch (Exception unused2) {
                queryParameter2 = null;
            }
        } catch (Exception unused3) {
            queryParameter = null;
            queryParameter2 = null;
        }
        Logger.verbose("User: " + queryParameter);
        Logger.verbose("Subject: " + queryParameter2);
        Logger.verbose("Message: " + queryParameter3);
        if (context instanceof BaseActivity) {
            EditFragment.newMessageInstanceWithSubject(queryParameter, queryParameter2, queryParameter3).show(((BaseActivity) context).getSupportFragmentManager(), "EditFragment");
        }
    }

    public static void openDomain(Context context, String str) {
        Logger.verbose("Opening domain link: " + str);
        CasualActivity.openDomain(context, str);
    }

    public static void openGfycat(Context context, Pair<View, String>[] pairArr, String subreddit, String url, String previewUrl, String title, String permalink, boolean visited, boolean blurNsfw) {
        if (SettingsSingleton.getInstance().getCurrentSettings().gfycat) {
            Logger.verbose("Opening Gfycat link internally: " + url);
            openImage(context, pairArr, subreddit, url, previewUrl, title, permalink, visited, blurNsfw, false);
            return;
        }
        Logger.verbose("Opening Gfycat link externally: " + url);
        openExternalOrInternal(context, url);
    }

    public static void openImage(Context context, Pair<View, String>[] pairArr, String subreddit, String url, String previewUrl, String title, String permalink, boolean visited, boolean blurNsfw, boolean infographic) {
        int finalWidth;
        int finalOverlayColor;
        int finalHeight;
        int finalDominantColor;
        Logger.verbose("Opening image link: " + url);
        String normalizedUrl = ImageHelper.normalizeImageUrl(url);
        String previewArg = infographic ? null : previewUrl;
        if (!SettingsSingleton.getInstance().getCurrentSettings().imageHandling) {
            openExternalOrInternal(context, normalizedUrl);
            return;
        }
        boolean animate = (Build.VERSION.SDK_INT >= 24 && (context instanceof BaseActivity) && ((BaseActivity) context).isInMultiWindowMode()) ? false : (!SettingsSingleton.getInstance().getCurrentSettings().animateTransitionImage || pairArr == null || infographic) ? false : true;
        int displayWidth = WindowUtils.getDisplayWidth();
        int displayHeight = WindowUtils.getDisplayHeight();
        if (pairArr != null) {
            int dominantColor = -16777216;
            int height = displayHeight;
            int overlayColor = 0;
            int width = displayWidth;
            for (int i = 0; i < pairArr.length; i++) {
                try {
                    View view = pairArr[i].first;
                    if ((view instanceof CustomImageView) && pairArr[i].second.equals("card_preview")) {
                        width = view.getWidth();
                        height = view.getHeight();
                        dominantColor = ((CustomImageView) view).getDominantColor();
                        overlayColor = ((CustomImageView) view).getOverlayColor();
                    }
                } catch (Exception e) {
                    Logger.logException(e);
                }
            }
            finalWidth = width;
            finalOverlayColor = overlayColor;
            finalHeight = height;
            finalDominantColor = dominantColor;
        } else {
            finalWidth = displayWidth;
            finalOverlayColor = 0;
            finalHeight = displayHeight;
            finalDominantColor = -16777216;
        }
        Intent intentH0 = AbstractImageActivity.createSingleIntent(context, subreddit, normalizedUrl, previewArg, title, permalink, animate, visited, blurNsfw, finalWidth, finalHeight, finalOverlayColor, finalDominantColor);
        if (!animate) {
            startActivity(context, intentH0);
        } else {
            Activity activity = (Activity) context;
            PreviousActivityHelper.startActivityWithPrevious(activity, intentH0, ActivityOptionsCompat.makeSceneTransitionAnimation(activity, pairArr).toBundle());
        }
    }

    public static void openMixtape(Context context, Pair<View, String>[] pairArr, String subreddit, String url, String previewUrl, String title, String permalink, boolean visited, boolean blurNsfw) {
        if (SettingsSingleton.getInstance().getCurrentSettings().mixtape) {
            Logger.verbose("Opening mixtape link internally: " + url);
            openImage(context, pairArr, subreddit, url, previewUrl, title, permalink, visited, blurNsfw, false);
            return;
        }
        Logger.verbose("Opening mixtape link externally: " + url);
        openExternalOrInternal(context, url);
    }

    public static void openMulti(Context context, String str) {
        Logger.verbose("Opening multi link: " + str);
        CasualActivity.openSubreddit(context, str);
    }

    public static void openProfile(Context context, String str) {
        Logger.verbose("Opening profile link: " + str);
        getUsername(str);
        CasualActivity.openUser(context, str);
    }

    public static void openRedgifs(Context context, Pair<View, String>[] pairArr, String subreddit, String url, String previewUrl, String title, String permalink, boolean visited, boolean blurNsfw) {
        if (SettingsSingleton.getInstance().getCurrentSettings().redgifs) {
            Logger.verbose("Opening RedGifs link internally: " + url);
            openImage(context, pairArr, subreddit, url, previewUrl, title, permalink, visited, blurNsfw, false);
            return;
        }
        Logger.verbose("Opening RedGifs link externally: " + url);
        openExternalOrInternal(context, url);
    }

    public static void openAlbum(Context context, String str, String str2, String str3, String str4, String str5) {
        Logger.verbose("Opening album link: " + str2);
        startActivity(context, AbstractImageActivity.createMultiIntent(context, str, str2, str3, str4, str5));
    }

    public static void openRedditVideo(Context context, Pair<View, String>[] pairArr, String subreddit, String url, String previewUrl, String title, String permalink, boolean visited, boolean blurNsfw) {
        if (SettingsSingleton.getInstance().getCurrentSettings().redditVideo) {
            Logger.verbose("Opening reddit video link internally: " + url);
            openImage(context, pairArr, subreddit, url, previewUrl, title, permalink, visited, blurNsfw, false);
            return;
        }
        Logger.verbose("Opening reddit video link externally: " + url);
        openExternalOrInternal(context, url);
    }

    public static void openRickroll(Context context, String str) {
        Logger.verbose("Opening rickroll link: " + str);
        if (context instanceof AppCompatActivity) {
            FragmentUtils.show(RickrollAlertDialogBottomSheet.class, ((AppCompatActivity) context).getSupportFragmentManager());
        } else if (context instanceof ContextThemeWrapper) {
            openRickroll(((ContextThemeWrapper) context).getBaseContext(), str);
        }
    }

    public static void openSearch(Context context, String str) {
        Logger.verbose("Opening search link: " + str);
        String subreddit = getSubreddit(str);
        Logger.verbose("Opening subreddit: " + subreddit);
        if (str != null && str.startsWith("/r/")) {
            str = "https://reddit.com" + str;
        }
        String strEncode = URLEncoder.encode(Uri.parse(str).getQueryParameter("q"));
        Logger.verbose("Query: " + strEncode);
        CasualActivity.openSubredditSearch(context, strEncode, subreddit);
    }

    public static void openShortened(Context context, String str) {
        Logger.verbose("Opening shortened link: " + str);
        openCommentsLink(context, "http://reddit.com/comments/" + getShortenedId(str));
    }

    public static void openStreamable(Context context, Pair<View, String>[] pairArr, String subreddit, String url, String previewUrl, String title, String permalink, boolean visited, boolean blurNsfw) {
        if (SettingsSingleton.getInstance().getCurrentSettings().streamable) {
            Logger.verbose("Opening streamable link internally: " + url);
            openImage(context, pairArr, subreddit, url, previewUrl, title, permalink, visited, blurNsfw, false);
            return;
        }
        Logger.verbose("Opening streamable link externally: " + url);
        openExternalOrInternal(context, url);
    }

    public static void openSubreddit(Context context, String str) {
        Logger.verbose("Opening subreddit link: " + str);
        if (context instanceof BaseActivity) {
            BaseActivity baseActivity = (BaseActivity) context;
            if (!LinkTypeHelper.isMulti(str)) {
                String subreddit = getSubreddit(str);
                if ("random_nsfw".equalsIgnoreCase(subreddit)) {
                    FragmentUtils.showWithArguments(RandomProgressDialogBottomSheet.class, baseActivity.getSupportFragmentManager(), RandomProgressDialogBottomSheet.buildArgs(true));
                    return;
                } else if ("random".equalsIgnoreCase(subreddit)) {
                    FragmentUtils.showWithArguments(RandomProgressDialogBottomSheet.class, baseActivity.getSupportFragmentManager(), RandomProgressDialogBottomSheet.buildArgs(false));
                    return;
                }
            }
        }
        CasualActivity.openSubreddit(context, str);
    }

    public static void openTumblr(Context context, String str, String str2, String str3, String str4, String str5) {
        Logger.verbose("Opening Tumblr link: " + str2);
        if (SettingsSingleton.getInstance().getCurrentSettings().tumblrPosts) {
            startActivity(context, AbstractImageActivity.createMultiIntent(context, str, str2, str3, str4, str5));
        } else {
            openExternalOrInternal(context, str2);
        }
    }

    public static void openExternalOrInternal(Context context, String str) {
        String host = LinkTypeHelper.getHost(str);
        if (host != null) {
            host = host.toLowerCase(Locale.ENGLISH);
        }
        if (BypassHelper.getInstance().getDomains().contains(host)) {
            openExternal(context, str);
            return;
        }
        if (SettingsSingleton.getInstance().getCurrentSettings().defaultBrowser == 0) {
            openCustomTab(context, str);
        } else if (SettingsSingleton.getInstance().getCurrentSettings().defaultBrowser == 1) {
            openWebView(context, str);
        } else {
            openExternal(context, str);
        }
    }

    public static void openCustomTab(Context context, String str) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(RedditApplication.getCustomTabsSession());
        builder.setToolbarColor(MonetUtil.getBackgroundColorAuto());
        builder.setShowTitle(true);
        builder.setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left);
        builder.setExitAnimations(context, R.anim.slide_in_left, R.anim.slide_out_right);
        builder.setUrlBarHidingEnabled(true);
        builder.addDefaultShareMenuItem();
        CustomTabsIntent customTabsIntentB = builder.build();
        if (SettingsSingleton.getSettings().incognitoTabs) {
            customTabsIntentB.intent.putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true);
        }
        if (!ChromeCustomTabsHelper.isAvailable()) {
            openWebView(context, str);
            return;
        }
        try {
            customTabsIntentB.launchUrl(context, Uri.parse(str));
        } catch (Exception unused) {
            openExternal(context, str);
        }
    }

    public static void openExternal(Context context, String str) {
        Logger.verbose("Opening link externally: " + str);
        if (context == null || str == null) {
            return;
        }
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse(str));
            startActivity(context, intent);
        } catch (ActivityNotFoundException unused) {
            StaticToast.showWithContext(context, "Could not open link:\n" + str);
        } catch (SecurityException e) {
            if (!TextUtils.isEmpty(e.getMessage()) && (e.getMessage().contains("com.alibaba.intl.android.apps.poseidon") || e.getMessage().contains("com.alibaba.aliexpresshd"))) {
                AlertDialogHelper.newBuilder(context).setMessage("Error opening link").setMessage("The Alibaba / Aliexpress app installed on your device is stopping links from being opened").setPositiveButton("Help", new AlibabaHelpClickListener(context)).show();
                return;
            }
            StaticToast.showWithContext(context, "Could not open link:\n" + str);
        }
    }

    public static void openWebView(Context context, String str) {
        Logger.verbose("Opening link internally: " + str);
        startActivity(context, WebViewActivity.createIntent(context, str));
    }

    public static void openVidme(Context context, Pair<View, String>[] pairArr, String subreddit, String url, String previewUrl, String title, String permalink, boolean visited, boolean blurNsfw) {
        if (SettingsSingleton.getInstance().getCurrentSettings().vidme) {
            Logger.verbose("Opening vid.me link internally: " + url);
            openImage(context, pairArr, subreddit, url, previewUrl, title, permalink, visited, blurNsfw, false);
            return;
        }
        Logger.verbose("Opening vidme link externally: " + url);
        openExternalOrInternal(context, url);
    }

    public static String getCommentsCommentId(String str) {
        if (!TextUtils.isEmpty(str)) {
            str = str.replaceAll("#", "");
        }
        if (str.endsWith("?")) {
            str = str.substring(0, str.length() - 1);
        }
        try {
            if (!str.contains("/comments/")) {
                return null;
            }
            String[] strArrSplit = str.split("/comments/")[1].split("/");
            if (strArrSplit.length >= 3) {
                return strArrSplit[2].contains("?") ? strArrSplit[2].split("\\?")[0] : strArrSplit[2];
            }
            return null;
        } catch (Exception e) {
            CrashlyticsHelper.logEvent(6, "LinkHandler", "Crashing getCommentsCommentId() URL: " + str);
            Logger.logException(e);
            return null;
        }
    }

    public static void openYouTube(Context context, Pair<View, String>[] pairArr, String str, String str2) {
        Logger.verbose("Opening video link: " + str);
        if (!SettingsSingleton.getInstance().getCurrentSettings().embedYouTube || str.contains("shared")) {
            openExternal(context, str);
            return;
        }
        Intent intent = new Intent(context, (Class<?>) CustomYouTubeVideoActivity.class);
        intent.putExtra("url", str);
        startActivity(context, intent);
    }

    public static String getCommentsId(String str) {
        try {
            if (str.contains("/comments")) {
                return str.split("/comments/")[1].split("/")[0];
            }
            String[] strArrSplit = str.split("/");
            return strArrSplit[strArrSplit.length - 1];
        } catch (Exception e) {
            CrashlyticsHelper.logEvent(6, "LinkHandler", "Crashing getCommentsId() URL: " + str);
            Logger.logException(e);
            return null;
        }
    }

    public static void openWiki(Context context, String str) {
        Logger.verbose("Opening wiki link: " + str);
        if (!(context instanceof AppCompatActivity)) {
            if (context instanceof ContextThemeWrapper) {
                openWiki(((ContextThemeWrapper) context).getBaseContext(), str);
            }
        } else {
            WikiBottomSheetFragment wikiBottomSheetFragment = new WikiBottomSheetFragment();
            Bundle bundle = new Bundle();
            bundle.putString(FragmentUtils.keyUrl(), str);
            wikiBottomSheetFragment.setArguments(bundle);
            wikiBottomSheetFragment.show(((AppCompatActivity) context).getSupportFragmentManager(), null);
        }
    }

    public static String getEroshareId(String str) {
        try {
            String id = str.contains("eroshare.com/i/") ? str.split("eroshare.com/i/")[1] : str.split("eroshare.com/")[1];
            return id.contains("?") ? id.split("\\?")[0] : id;
        } catch (Exception e) {
            CrashlyticsHelper.logException(e);
            Logger.logException(e);
            return null;
        }
    }

    public static void startActivity(Context context, Intent intent) {
        if (context instanceof BaseMaterialDragActivity) {
            ((BaseMaterialDragActivity) context).restoreFromTranslucent();
        }
        if (TestUtils.isRobolectric()) {
            intent.setFlags(268435456);
        }
        if (!(context instanceof AppCompatActivity)) {
            intent.setFlags(268435456);
        }
        context.startActivity(intent);
    }

    public static String getGfycatId(String str) {
        try {
            // Strip the query and fragment before the trailing-slash cleanup: a URL like
            // redgifs.com/watch/<id>/?92/ would otherwise resolve to an empty id and 404.
            int query = str.indexOf('?');
            if (query >= 0) {
                str = str.substring(0, query);
            }
            int fragment = str.indexOf('#');
            if (fragment >= 0) {
                str = str.substring(0, fragment);
            }
            if (str.endsWith("/")) {
                str = str.substring(0, str.length() - 1);
            }
            String strReplace = str.substring(str.lastIndexOf("/") + 1, str.length()).replace(".gif", "").replace(".mp4", "").replace(".webm", "").replace("-mobile", "").replace("-size_restricted", "").replace("-max-1mb-poster", "");
            return strReplace.contains("-") ? strReplace.split("-")[0] : strReplace;
        } catch (Exception e) {
            CrashlyticsHelper.logEvent(6, "LinkHandler", "Crashing getGfycatId() URL: " + str);
            Logger.logException(e);
            return null;
        }
    }

    public static String getGiphyId(String str) {
        if (str.contains("/media/")) {
            return str.split("/media/")[1].split("/")[0];
        }
        if (!str.contains("/gifs/")) {
            return null;
        }
        if (str.endsWith("/")) {
            str = str.substring(0, str.lastIndexOf("/"));
        }
        if (str.endsWith("/html5")) {
            str = str.substring(0, str.indexOf("/html5"));
        }
        String[] strArrSplit = str.split("/");
        String str2 = strArrSplit[strArrSplit.length - 1];
        int iLastIndexOf = str2.lastIndexOf("-");
        return iLastIndexOf != -1 ? str2.substring(iLastIndexOf + 1, str2.length()) : str2;
    }

    public static String getImgflipId(String str) {
        String str2 = str.split("imgflip.com/i/")[1];
        return str2.contains("#") ? str2.split("#")[0] : str2;
    }

    public static String getCommentsSubreddit(String str) {
        try {
            return str.startsWith("r/") ? str.split("r/")[1].split("/")[0] : str.split("/r/")[1].split("/")[0];
        } catch (Exception unused) {
            return null;
        }
    }

    public static String getLastPathSegment(String str) {
        String[] strArrSplit = str.split("/");
        String str2 = strArrSplit[strArrSplit.length - 1];
        return str2.contains(".") ? str2.split("\\.")[0] : str2;
    }

    public static String getUserFromPath(String str) {
        return str.contains("/user/") ? str.substring(str.indexOf("/user/") + 6) : str.contains("/u/") ? str.substring(str.indexOf("/u/") + 3) : str;
    }

    public static String getRedditImageId(String str) {
        if (str.contains("i.redd.it/")) {
            str = str.split("i.redd.it/")[1];
        } else if (str.contains("preview.redd.it/")) {
            str = str.split("preview.redd.it/")[1];
        }
        return str.contains(".") ? str.split("\\.")[0] : str;
    }

    public static String getRedditVideoId(String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        String strTrim = str.trim();
        try {
            strTrim = normalizeUri(strTrim);
        } catch (Exception e) {
            Logger.logException(e);
        }
        if (strTrim.endsWith("/")) {
            strTrim = strTrim.substring(0, strTrim.length() - 1);
        }
        String strReplace = strTrim.replace("/HLSPlaylist.m3u8", "").replace("/DASHPlaylist.mpd", "").replace("/DASH_240", "").replace("/DASH_360", "").replace("/DASH_480", "").replace("/DASH_600_K", "").replace("/DASH_720", "").replace("/DASH_1080", "").replace("/DASH_1_2_M", "").replace("/DASH_2_4_M", "").replace("/DASH_4_8_M", "").replace("/DASH_9_6_M", "");
        return strReplace.substring(strReplace.lastIndexOf("/") + 1);
    }

    public static String getShortenedId(String str) {
        try {
            if (str.contains("redd.it/")) {
                return str.split("redd.it/")[1];
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
        CrashlyticsHelper.logEvent(6, "LinkHandler", "Crashing getShortenedId() URL: " + str);
        return null;
    }

    public static String getSlimgId(String str) {
        CrashlyticsHelper.logEvent(6, "getSlimgId", str);
        try {
            String id = str.split("sli.mg/")[1];
            return id.contains(".") ? id.split("\\.")[0] : id;
        } catch (Exception e) {
            CrashlyticsHelper.logException(e);
            Logger.logException(e);
            return str;
        }
    }

    public static String getLastSegmentId(String str) {
        String[] strArrSplit = str.split("/");
        String str2 = strArrSplit[strArrSplit.length - 1];
        if (str2.contains(".")) {
            str2 = str2.split("\\.")[0];
        }
        return str2.contains("?") ? str2.split("\\?")[0] : str2;
    }

    public static String getSubreddit(String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        String strTrim = str.trim();
        if (strTrim.contains("reddit.com/r/")) {
            Matcher matcher = Pattern.compile("reddit.com/r/([\\.\\+a-zA-Z0-9_-]*)( |\\n|$|\\W)", 8).matcher(strTrim);
            return matcher.find() ? matcher.group(1) : strTrim;
        }
        if (strTrim.startsWith("r/")) {
            String strReplace = strTrim.replace("r/", "");
            return strReplace.contains("/") ? strReplace.split("/")[0] : strReplace;
        }
        if (!strTrim.startsWith("/r/")) {
            return strTrim;
        }
        String strReplace2 = strTrim.replace("/r/", "");
        return strReplace2.contains("/") ? strReplace2.split("/")[0] : strReplace2;
    }

    public static String getTumblrSubdomain(String str) {
        if (android.text.TextUtils.isEmpty(str)) {
            return null;
        }
        return Uri.parse(str).getHost().split("\\.")[0];
    }

    public static String getTumblrPostId(String str) {
        if (android.text.TextUtils.isEmpty(str)) {
            return null;
        }
        try {
            return Uri.parse(str).getPathSegments().get(1);
        } catch (Exception unused) {
            CrashlyticsHelper.logEvent(6, "Tumblr", "Invalid: " + str);
            return "";
        }
    }

    private static String normalizeUri(String str) throws URISyntaxException {
        URI uri = new URI(str);
        return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, uri.getFragment()).toString();
    }

    public static String getUsername(String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        String strTrim = str.trim();
        if (strTrim.contains("reddit.com/u/")) {
            Matcher matcher = Pattern.compile("reddit.com/u/([a-zA-Z0-9_-]*)( |\\n|$|\\W)", 8).matcher(strTrim);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (strTrim.contains("reddit.com/user/")) {
            Matcher matcher2 = Pattern.compile("reddit.com/user/([a-zA-Z0-9_-]*)( |\\n|$|\\W)", 8).matcher(strTrim);
            if (matcher2.find()) {
                return matcher2.group(1);
            }
        }
        return strTrim.startsWith("u/") ? strTrim.replace("u/", "") : strTrim.startsWith("/u/") ? strTrim.replace("/u/", "") : strTrim;
    }

    public static String getWikiPage(String str) {
        try {
            if (str.contains("/w/")) {
                String strReplaceAll = str.split("/w/")[1].replaceAll("/$", "");
                return strReplaceAll.contains("#") ? strReplaceAll.split("#")[0] : strReplaceAll;
            }
            if (!str.contains("/wiki/")) {
                return null;
            }
            String strReplaceAll2 = str.split("/wiki/")[1].replaceAll("/$", "");
            return strReplaceAll2.contains("#") ? strReplaceAll2.split("#")[0] : strReplaceAll2;
        } catch (Exception unused) {
            return null;
        }
    }

    public static String getXkcdId(String str) {
        return str.toLowerCase(Locale.ENGLISH).split("xkcd.com/")[1].replace("/", "");
    }

    public static String getYouTubeId(String str) {
        String strSubstring;
        if (str == null) {
            return str;
        }
        try {
            CrashlyticsHelper.logEvent(6, "getYouTubeId", str);
            String strReplaceAll = URLDecoder.decode(str).replaceAll("\\&amp;", "\\&");
            if (strReplaceAll == null || strReplaceAll.trim().length() <= 0) {
                return strReplaceAll;
            }
            if (!StringUtils.containsIgnoreCase(strReplaceAll, "youtube.com")) {
                strSubstring = strReplaceAll.toLowerCase().contains("youtu.be/") ? strReplaceAll.substring(strReplaceAll.toLowerCase().indexOf("youtu.be/") + 9) : null;
            } else if (strReplaceAll.contains("v=")) {
                try {
                    strSubstring = strReplaceAll.split("v=")[1];
                } catch (Exception unused) {
                    return null;
                }
            } else {
                if (!strReplaceAll.contains("v/")) {
                    return strReplaceAll;
                }
                strSubstring = strReplaceAll.split("v/")[1];
            }
            if (strSubstring.contains("&")) {
                strSubstring = strSubstring.split("&")[0];
            }
            if (strSubstring.contains("#")) {
                strSubstring = strSubstring.split("#")[0];
            }
            if (strSubstring.contains("?")) {
                strSubstring = strSubstring.split("\\?")[0];
            }
            return strSubstring.contains("%") ? strSubstring.split("\\%")[0] : strSubstring;
        } catch (Exception unused2) {
            return null;
        }
    }

    public static int getYouTubeTimestamp(String url) {
        int seconds;
        int timestamp = 0;
        if (TextUtils.isEmpty(url)) {
            return 0;
        }
        String queryParameter = Uri.parse(url).getQueryParameter("t");
        Logger.verbose("Timestamp: " + queryParameter);
        try {
            if (TextUtils.isEmpty(queryParameter)) {
                return 0;
            }
            timestamp = queryParameter.contains("h") ? 0 + (Integer.parseInt(queryParameter.substring(0, queryParameter.indexOf("h"))) * 3600) : 0;
            if (queryParameter.contains("m")) {
                timestamp += Integer.parseInt(queryParameter.substring(queryParameter.indexOf("h") + 1, queryParameter.indexOf("m"))) * 60;
            }
            if (queryParameter.contains("s")) {
                int iIndexOf = queryParameter.indexOf("m") + 1;
                int iIndexOf2 = queryParameter.indexOf("s");
                if (iIndexOf == iIndexOf2) {
                    return timestamp;
                }
                seconds = Integer.parseInt(queryParameter.substring(iIndexOf, iIndexOf2));
            } else {
                int iIndexOf3 = queryParameter.indexOf("m") + 1;
                int length = queryParameter.length();
                if (iIndexOf3 == length) {
                    return timestamp;
                }
                seconds = Integer.parseInt(queryParameter.substring(iIndexOf3, length));
            }
            timestamp += seconds;
            return timestamp;
        } catch (Exception e) {
            CrashlyticsHelper.logException(e);
            Logger.logException(e);
            return timestamp;
        }
    }

    public static boolean isNoParticipation(String str) {
        return !TextUtils.isEmpty(str) && str.contains("np.reddit.com");
    }

    public static void openAgeVerification(Context context, String str) {
        Logger.verbose("Opening age verification link: " + str);
        LinkHelper.open(context, URLDecoder.decode(str.substring(35)));
    }

    public static void openImgurAlbum(Context context, String str, String str2, String str3, String str4, String str5) {
        Logger.verbose("Opening album link: " + str2);
        if (SettingsSingleton.getInstance().getCurrentSettings().imgurGallery) {
            startActivity(context, AbstractImageActivity.createMultiIntent(context, str, str2, str3, str4, str5));
        } else {
            openExternalOrInternal(context, str2);
        }
    }
}
