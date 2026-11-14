package com.resync.app;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.Resources;
import androidx.core.content.ContextCompat;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieSyncManager;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.work.Configuration;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.resync.app.receiver.SystemMonetThemeReceiver;
import com.resync.app.singleton.SettingsSingleton;
import com.resync.app.singleton.UiSingleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.resync.app.net.OkHttpHelper;
import com.resync.app.helpers.PerSubSortHelper;
import com.resync.app.helpers.PerSubViewModeHelper;
import com.resync.app.helpers.CustomTabsHelper;
import com.resync.app.helpers.CrashlyticsHelper;
import com.resync.app.helpers.NightHelper;
import com.resync.app.helpers.NotificationHelper;
import com.resync.app.util.Logger;
import com.resync.app.util.TestUtils;
import com.resync.app.util.TextUtils;
import com.resync.app.model.Post;
import androidx.browser.customtabs.CustomTabsServiceConnection;

/* JADX INFO: loaded from: classes.dex */
public class RedditApplication extends Application implements Configuration.Provider {
    public static Bitmap playGiphyBitmap;
    public static Bitmap playExternalBitmap;
    public static Bitmap playSpoilerBitmap;
    public static Bitmap playRedditBitmap;
    public static Bitmap playTextBitmap;
    public static Drawable playVideoDrawable;
    public static Bitmap linkSpanExternalBitmap;
    public static Bitmap linkSpanSubBitmap;
    public static Bitmap linkSpanGifBitmap;
    public static Bitmap linkSpanImageBitmap;
    public static Bitmap linkSpanVideoBitmap;
    public static Bitmap linkSpanCommentBitmap;
    public static Bitmap linkSpanProfileBitmap;
    public static Bitmap linkSpanTableBitmap;
    public static Bitmap awardBitmap;
    public static Drawable linkDrawable;
    public static long startupTime = System.currentTimeMillis();
    public static ExecutorService threadPool;

    private static Context appContext;
    public static RequestQueue mainRequestQueue;
    public static RequestQueue oauthRequestQueue;
    public static com.resync.app.net.RequestQueueTracker bTracker;
    public static com.resync.app.net.RequestQueueTracker cTracker;

    public static RequestQueue thirdPartyRequestQueue;

    private static CustomTabsClient customTabsClient;

    private static CustomTabsSession customTabsSession;
    public static Bitmap playGifBitmap;

    public static Bitmap playVideoBitmap;
    public static Bitmap playAlbumBitmap;
    public static Bitmap playNsfwBitmap;
    public static Bitmap playStreamableBitmap;

    public static Bitmap playGfycatBitmap;
    public static Bitmap playRedgifsBitmap;

    class AssetLoaderThread extends Thread {
        AssetLoaderThread() {
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            RedditApplication.this.loadAssets();
        }
    }

    class CustomTabsBindThread extends Thread {

        class CustomTabsConnection extends CustomTabsServiceConnection {

            /* JADX INFO: renamed from: com.resync.app.RedditApplication$b$a$a, reason: collision with other inner class name */
            class TabsCallback extends CustomTabsCallback {
                TabsCallback() {
                }

                @Override // androidx.browser.customtabs.CustomTabsCallback
                public void onNavigationEvent(int navigationEvent, Bundle bundle) {
                    Logger.verbose("Navigation event: " + navigationEvent);
                    super.onNavigationEvent(navigationEvent, bundle);
                }
            }

            CustomTabsConnection() {
            }

            @Override // androidx.browser.customtabs.CustomTabsServiceConnection
            public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient customTabsClient) {
                Logger.verbose("Chrome custom tabs connected");
                CustomTabsClient unused = RedditApplication.customTabsClient = customTabsClient;
                try {
                    RedditApplication.customTabsClient.warmup(0L);
                } catch (Exception e) {
                    CrashlyticsHelper.logException(e);
                }
                try {
                    CustomTabsSession unused2 = RedditApplication.customTabsSession = RedditApplication.customTabsClient.newSession(new TabsCallback());
                } catch (Exception e) {
                    CrashlyticsHelper.logException(e);
                }
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName componentName) {
                Logger.verbose("Chrome custom tabs disconnected");
                CustomTabsClient unused = RedditApplication.customTabsClient = null;
                CustomTabsSession unused2 = RedditApplication.customTabsSession = null;
            }
        }

        CustomTabsBindThread() {
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            RedditApplication redditApplication = RedditApplication.this;
            CustomTabsClient.bindCustomTabsService(redditApplication, CustomTabsHelper.getPackageNameToUse(redditApplication), new CustomTabsConnection());
        }
    }

    public static Context getAppContext() {
        return appContext;
    }

    private static java.lang.ref.WeakReference<android.app.Activity> currentActivityRef;

    /** The currently-resumed Activity (or null). Used e.g. to attach an off-screen WebView to a real window. */
    public static android.app.Activity getCurrentActivity() {
        return currentActivityRef != null ? currentActivityRef.get() : null;
    }

    public static int getInteger(int resId) {
        return appContext.getResources().getInteger(resId);
    }

    public static CustomTabsSession getCustomTabsSession() {
        return customTabsSession;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadAssets() {
        CookieSyncManager.createInstance(this);
        Resources resources = getResources();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferQualityOverSpeed = false;
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inScaled = true;
        playGifBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_gif, options);
        playVideoBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_video, options);
        playVideoDrawable = getResources().getDrawable(R.drawable.play_video);
        playAlbumBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_album, options);
        playNsfwBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_nsfw, options);
        playStreamableBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_streamable, options);
        playGfycatBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_gfycat, options);
        playRedgifsBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_redgifs, options);
        playGiphyBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_giphy, options);
        playExternalBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_external, options);
        playSpoilerBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_spoiler, options);
        playRedditBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_reddit, options);
        playTextBitmap = BitmapFactory.decodeResource(resources, R.drawable.play_text, options);
        linkSpanSubBitmap = BitmapFactory.decodeResource(resources, R.drawable.link_span_sub, options);
        linkSpanGifBitmap = BitmapFactory.decodeResource(resources, R.drawable.link_span_gif, options);
        linkSpanImageBitmap = BitmapFactory.decodeResource(resources, R.drawable.link_span_image, options);
        linkSpanVideoBitmap = BitmapFactory.decodeResource(resources, R.drawable.link_span_video, options);
        linkSpanCommentBitmap = BitmapFactory.decodeResource(resources, R.drawable.link_span_comment, options);
        linkSpanProfileBitmap = BitmapFactory.decodeResource(resources, R.drawable.link_span_profile, options);
        linkSpanExternalBitmap = BitmapFactory.decodeResource(resources, R.drawable.link_span_external, options);
        linkSpanTableBitmap = BitmapFactory.decodeResource(resources, R.drawable.link_span_table, options);
        awardBitmap = BitmapFactory.decodeResource(resources, R.drawable.award, options);
        linkDrawable = getResources().getDrawable(R.drawable.outline_link_24);
        if (TextUtils.isEmpty(CustomTabsHelper.getPackageNameToUse(this)) || TestUtils.isRobolectric()) {
            return;
        }
        new CustomTabsBindThread().start();
    }

    public static void setupCrashlytics() {
        // ReSync: Crashlytics removed; nothing to set up.
        Logger.verbose("Trackers setup (no-op)");
    }

    public static void warmUpCustomTab(Post post) {
        if (((post == null || post.getSyncType() != 3) && post.getSyncType() != 9) || customTabsClient == null || customTabsSession == null || !SettingsSingleton.getInstance().getCurrentSettings().cctPreload) {
            return;
        }
        Logger.verbose("Warming up session!");
        customTabsClient.warmup(0L);
        customTabsSession.mayLaunchUrl(Uri.parse(post.getUrl()), null, null);
    }

    @Override // androidx.work.Configuration.Provider
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().setMinimumLoggingLevel(4).build();
    }

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {}
            @Override public void onActivityStarted(android.app.Activity a) {}
            @Override public void onActivityResumed(android.app.Activity a) {
                currentActivityRef = new java.lang.ref.WeakReference<>(a);
            }
            @Override public void onActivityPaused(android.app.Activity a) {}
            @Override public void onActivityStopped(android.app.Activity a) {}
            @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
            @Override public void onActivityDestroyed(android.app.Activity a) {
                if (currentActivityRef != null && currentActivityRef.get() == a) {
                    currentActivityRef = null;
                }
            }
        });
        threadPool = Executors.newFixedThreadPool(1);
        if (TestUtils.isRobolectric()) {
            setTheme(R.style.RedditSync_Activity);
        }
        Logger.verbose("Startup time first: " + (System.currentTimeMillis() - startupTime));
        appContext = this;
        NotificationHelper.createPassiveChannel();
        NotificationHelper.createGenericChannel();
        NotificationHelper.createErrorChannel();
        NotificationHelper.createMessagingChannel();
        NotificationHelper.createUltraChannel();
        OkHttpHelper.initClients();
        Logger.verbose("Startup time http: " + (System.currentTimeMillis() - startupTime));
        PerSubSortHelper.load();
        PerSubViewModeHelper.load();
        Logger.verbose("Startup time per sub: " + (System.currentTimeMillis() - startupTime));
        UiSingleton.getInstance();
        Logger.verbose("Startup time ui: " + (System.currentTimeMillis() - startupTime));
        SettingsSingleton.getInstance();
        Logger.verbose("Startup time setting singleton: " + (System.currentTimeMillis() - startupTime));
        NightHelper.refresh(false);
        Logger.verbose("Startup time night: " + (System.currentTimeMillis() - startupTime));
        setupCrashlytics();
        mainRequestQueue = Volley.newRequestQueue(this);
        oauthRequestQueue = Volley.newRequestQueue(this, new com.resync.app.net.WebViewRoutingStack(this));
        bTracker = com.resync.app.net.RequestQueueTracker.attach(mainRequestQueue);
        cTracker = com.resync.app.net.RequestQueueTracker.attach(oauthRequestQueue);
        thirdPartyRequestQueue = Volley.newRequestQueue(this);
        if (TestUtils.isRobolectric()) {
            loadAssets();
        } else {
            new AssetLoaderThread().start();
        }
        Logger.verbose("Startup time final: " + (System.currentTimeMillis() - startupTime));
        IntentFilter intentFilter = new IntentFilter("android.intent.action.OVERLAY_CHANGED");
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart("android", 0);
        // Android 14 (targetSdk 34+) requires an export flag on context-registered receivers.
        // OVERLAY_CHANGED is a system broadcast, but mark NOT_EXPORTED for safety/consistency with the
        // other dynamic receivers — the matching DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION is in the manifest.
        ContextCompat.registerReceiver(this, new SystemMonetThemeReceiver(), intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override // android.app.Application, android.content.ComponentCallbacks
    public void onLowMemory() {
        super.onLowMemory();
    }
}
