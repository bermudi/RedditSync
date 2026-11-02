package com.resync.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.resync.app.RedditApplication;
import java.util.Map;

/**
 * Remembers the last image viewed in an album, by the URL the album was loaded from, so opening
 * it again starts where the user left off.
 *
 * <p>This used to ride in the Volley cache entry — the album's JSON response was rewritten with a
 * "position" field behind the request's back — which stopped being possible when the album
 * replies stopped being the JSON the rewriting understood. A preferences file keyed the same way
 * serves the same purpose without depending on a response shape.
 */
public final class GalleryPositions {

    private static final String PREFS_NAME = "gallery_positions";

    /** Albums ever opened is unbounded; past this the history is simply dropped. */
    private static final int MAX_ENTRIES = 200;

    private GalleryPositions() {
    }

    public static void save(String key, int position) {
        SharedPreferences prefs = prefs();
        prefs.edit().putInt(key, position).apply();
        if (prefs.getAll().size() > MAX_ENTRIES) {
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                editor.remove(entry.getKey());
            }
            editor.apply();
        }
    }

    public static int get(String key) {
        return prefs().getInt(key, 0);
    }

    private static SharedPreferences prefs() {
        Context context = RedditApplication.getAppContext();
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
