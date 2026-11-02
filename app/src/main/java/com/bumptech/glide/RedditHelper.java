package com.bumptech.glide;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import com.resync.app.helpers.ui.StaticToast;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.resync.app.text.html.SyncHtmlUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import com.resync.app.singletons.MarkdownSingleton;
import com.resync.app.helpers.CrashlyticsHelper;
import com.resync.app.util.Logger;
import com.resync.app.util.TestUtils;
import com.resync.app.util.WebsafeUtils;

/* JADX INFO: renamed from: d7.f */
/* JADX INFO: compiled from: RedditHelper.java */
/* JADX INFO: loaded from: classes.dex */
public abstract class RedditHelper {
    public static void copyToClipboard(Context context, CharSequence charSequence, boolean showToast) {
        try {
            ((ClipboardManager) context.getSystemService("clipboard")).setPrimaryClip(ClipData.newPlainText("reddit sync link", charSequence));
            if (showToast) {
                StaticToast.showWithContext("Text copied to clipboard", context);
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    @SuppressLint({"ServiceCast"})
    public static void copyToClipboardWithToast(CharSequence charSequence, Context context) {
        try {
            ((ClipboardManager) context.getSystemService("clipboard")).setPrimaryClip(ClipData.newPlainText("reddit sync link", charSequence));
            StaticToast.showWithContext("Text copied to clipboard", context);
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    @SuppressLint({"ServiceCast"})
    public static void copyUrlToClipboard(Context context, String str) {
        ((ClipboardManager) context.getSystemService("clipboard")).setPrimaryClip(ClipData.newPlainText("reddit sync link", str));
        StaticToast.showWithContext("URL copied to clipboard", context);
    }

    public static String escapeCodeBlocks(String str) {
        StringBuffer stringBuffer = new StringBuffer();
        Matcher matcher = Pattern.compile("<code>(.*?)</code>", 32).matcher(str);
        while (matcher.find()) {
            try {
                String strGroup = matcher.group(0);
                if (!TextUtils.isEmpty(strGroup)) {
                    boolean zContains = strGroup.contains("\n");
                    String strReplaceAll = strGroup.replaceAll("\n", "<br/>").replaceAll("\\\\", "&#92;").replaceAll("\\$", "&#36;").replaceAll("&amp;lt;", "&lt;").replaceAll("&amp;gt;", "&gt;").replaceAll(StringUtils.SPACE, "&nbsp;");
                    if (zContains) {
                        strReplaceAll = strReplaceAll.replace("<code>", "<" + SyncHtmlUtils.SAMP + ">").replace("</code>", "</" + SyncHtmlUtils.SAMP + ">");
                    }
                    // Code block text goes back in as a regex replacement string; $ and \ in it
                    // would otherwise be read as group references (corrupted text, or a throw).
                    matcher.appendReplacement(stringBuffer, Matcher.quoteReplacement(strReplaceAll));
                }
            } catch (IndexOutOfBoundsException e) {
                Logger.logException(e);
            }
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    public static String convertFencedCodeBlocks(String str) {
        StringBuffer stringBuffer = new StringBuffer();
        Matcher matcher = Pattern.compile("```(.*?)```", 32).matcher(str);
        while (matcher.find()) {
            try {
                // Fenced code is not $-escaped before this (unlike escapeCodeBlocks), so a $ in it
                // reaches appendReplacement as a group reference and breaks the thread's rendering.
                matcher.appendReplacement(stringBuffer, Matcher.quoteReplacement(StringUtils.replaceFirst(StringUtils.replaceFirst(matcher.group(0).replaceAll("\n", "\n    "), "```", "    "), "```", "    \n-")));
            } catch (IndexOutOfBoundsException e) {
                Logger.logException(e);
            }
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    public static String fixRelativeLinks(String str) {
        return str.replaceAll("\\(/about/postcards/\\)", "(https://reddit.com/about/postcards/)").replaceAll("\\(/message/compose/", "\\(http://reddit.com/message/compose/");
    }

    public static String redactNamedLinks(String str) {
        String strGroup;
        String strGroup2;
        String strGroup3;
        StringBuffer stringBuffer = new StringBuffer();
        Matcher matcher = Pattern.compile("\\[([áàäêçüïñ a-zA-Z0-9 \\$\\-\"`'/&,!£<>#~@;:_\\}\\{\\*\\^\\+\\(\\)\\.\\?\\\\]*)\\]( )*\\(( )*(/[a-z\\.]{1,10}|#[a-z\\.]{1,10}|/wiki/)( )*(\"(.*?)\")*\\)", 8).matcher(str);
        while (matcher.find()) {
            try {
                strGroup = matcher.group(6);
                strGroup2 = matcher.group(1);
                strGroup3 = matcher.group(4);
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
                continue;
            }
            if (!TextUtils.isEmpty(strGroup) || !TextUtils.isEmpty(strGroup2) || TextUtils.isEmpty(strGroup3)) {
                if (TextUtils.isEmpty(strGroup2) && !TextUtils.isEmpty(strGroup)) {
                    String strReplaceAll = strGroup.replaceAll("\"", "");
                    if (!TextUtils.isEmpty(strReplaceAll)) {
                        strReplaceAll = strReplaceAll.trim();
                    }
                    matcher.appendReplacement(stringBuffer, Matcher.quoteReplacement("[" + strReplaceAll.replaceAll(".", "█") + "](" + URLEncoder.encode(strReplaceAll) + ")"));
                } else if (TextUtils.isEmpty(strGroup2) || TextUtils.isEmpty(strGroup)) {
                    matcher.appendReplacement(stringBuffer, Matcher.quoteReplacement("[" + strGroup2.replaceAll(".", "█") + "](" + URLEncoder.encode(strGroup2) + ")"));
                } else {
                    String strReplaceAll2 = strGroup.replaceAll("\"", "");
                    String strReplaceAll3 = strGroup2.replaceAll("\"", "");
                    if (!TextUtils.isEmpty(strReplaceAll3)) {
                        strReplaceAll3 = strReplaceAll3.trim() + ": ";
                    }
                    matcher.appendReplacement(stringBuffer, Matcher.quoteReplacement("[" + strReplaceAll3 + strReplaceAll2.replaceAll(".", "█") + "](" + URLEncoder.encode(strReplaceAll2) + ")"));
                }
            }
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    public static String escapeMultipleSpaces(String str) {
        StringBuffer stringBuffer = new StringBuffer();
        Matcher matcher = Pattern.compile("( ){3,}+", 32).matcher(str);
        while (matcher.find()) {
            try {
                String strGroup = matcher.group(0);
                StringBuilder sb2 = new StringBuilder();
                for (int i = 0; i < strGroup.length(); i++) {
                    sb2.append("&nbsp;");
                }
                matcher.appendReplacement(stringBuffer, sb2.toString());
            } catch (IndexOutOfBoundsException e) {
                Logger.logException(e);
            }
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    public static String escapeQuotes(String str) {
        StringBuffer stringBuffer = new StringBuffer();
        Matcher matcher = Pattern.compile("^(>){1,}+", 8).matcher(str);
        while (matcher.find()) {
            try {
                String strGroup = matcher.group(0);
                StringBuilder sb2 = new StringBuilder();
                for (int i = 0; i < strGroup.length(); i++) {
                    sb2.append("QUOTE_ESCAPE");
                }
                matcher.appendReplacement(stringBuffer, sb2.toString());
            } catch (IndexOutOfBoundsException e) {
                Logger.logException(e);
            }
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    @Deprecated
    public static CharSequence htmlToTrimmedText(String str) {
        if (str == null) {
            return null;
        }
        try {
            Spanned spannedFromHtml = Html.fromHtml(str);
            return trimWhitespace(spannedFromHtml, 0, spannedFromHtml.length());
        } catch (Exception e) {
            CrashlyticsHelper.logException(e);
            Spanned spannedFromHtml2 = Html.fromHtml(str);
            return trimWhitespace(spannedFromHtml2, 0, spannedFromHtml2.length());
        }
    }

    public static int getTitleTextSize(int textSizeLevel) {
        return getBodyTextSize(textSizeLevel) + 6;
    }

    public static int getSubtitleTextSize(int textSizeLevel) {
        return getBodyTextSize(textSizeLevel) + 5;
    }

    public static int getFixedLargeTextSize() {
        return 36;
    }

    public static int getHeaderTextSize(int textSizeLevel) {
        return getBodyTextSize(textSizeLevel) + 8;
    }

    public static int getMediumTextSize(int textSizeLevel) {
        return getBodyTextSize(textSizeLevel) + 4;
    }

    public static int getSmallHeaderTextSize(int textSizeLevel) {
        return getBodyTextSize(textSizeLevel) + 2;
    }

    public static int getBodyTextSize(int textSizeLevel) {
        if (textSizeLevel == 0) {
            return 8;
        }
        switch (textSizeLevel) {
            case 2:
                return 12;
            case 3:
                return 14;
            case 4:
                return 16;
            case 5:
                return 18;
            case 6:
                return 20;
            case 7:
                return 24;
            default:
                return 10;
        }
    }

    public static String processMarkdownToHtml(String str, String str2) {
        if (TextUtils.isEmpty(str2) || str2.equalsIgnoreCase("#")) {
            return "";
        }
        String body = WebsafeUtils.unescapeHtml(redactNamedLinks(StringUtils.stripEnd(str2.replaceAll(IOUtils.LINE_SEPARATOR_WINDOWS, "\n\n").replaceAll("\\\\>!", "ESCAPED_SPOILER"), StringUtils.SPACE).replaceAll("\\&#x200B\\;", "")));
        Logger.verbose("BODY: " + body);
        String fixedBody = fixRelativeLinks(escapeQuotes(body.replaceAll("\\\\<", "ESCAPED_OPEN").replaceAll("\\\\>", "ESCAPED_CLOSE")).replaceAll(">!", "SPOILER_OPEN").replaceAll("!<", "SPOILER_CLOSE").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("SPOILER_OPEN", ">!").replaceAll("SPOILER_CLOSE", "!<").replaceAll("QUOTE_ESCAPE", ">"));
        if (TestUtils.isRobolectric()) {
            return fixedBody;
        }
        return escapeMultipleSpaces(StringUtils.trim(escapeCodeBlocks(MarkdownSingleton.getAndDown().markdownToHtml(convertFencedCodeBlocks(fixedBody)))).replaceAll("<spoiler>", "<" + SyncHtmlUtils.ABBR + ">").replaceAll("</spoiler>", "</" + SyncHtmlUtils.ABBR + ">")).replaceAll("ESCAPED_SPOILER", ">!").replaceAll("ESCAPED_OPEN", "&lt;").replaceAll("ESCAPED_CLOSE", "&gt;");
    }

    public static void shareText(Context context, String str) {
        Intent intent = new Intent("android.intent.action.SEND");
        intent.putExtra("android.intent.extra.TEXT", str);
        intent.setType("text/plain");
        context.startActivity(Intent.createChooser(intent, "Share"));
    }

    public static void shareTextWithSubject(Context context, String str, String str2) {
        Intent intent = new Intent("android.intent.action.SEND");
        intent.putExtra("android.intent.extra.SUBJECT", str);
        intent.putExtra("android.intent.extra.TEXT", str2);
        intent.setType("text/plain");
        context.startActivity(Intent.createChooser(intent, "Share"));
    }

    public static void sharePostLink(Context context, String str, String str2) {
        Intent intent = new Intent("android.intent.action.SEND");
        if (!TextUtils.isEmpty(str)) {
            intent.putExtra("android.intent.extra.SUBJECT", str);
        }
        if (str2.contains("v.redd.it")) {
            str2 = "https://v.redd.it/" + com.resync.app.util.links.LinkHandler.getRedditVideoId(str2);
        }
        intent.putExtra("android.intent.extra.TEXT", str2);
        intent.setType("text/plain");
        context.startActivity(Intent.createChooser(intent, "Share"));
    }

    public static CharSequence trimWhitespace(CharSequence charSequence, int start, int end) {
        while (start < end && Character.isWhitespace(charSequence.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(charSequence.charAt(end - 1))) {
            end--;
        }
        return charSequence.subSequence(start, end);
    }
}
