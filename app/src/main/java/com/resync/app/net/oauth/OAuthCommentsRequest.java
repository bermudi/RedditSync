package com.resync.app.net.oauth;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.resync.app.provider.RedditProvider;
import com.resync.app.singleton.SettingsSingleton;
import java.util.ArrayList;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import com.resync.app.text.parsing.CommentParserHelper;
import com.resync.app.text.parsing.ContentValuesListHelper;
import com.resync.app.net.OAuthRequestManager;
import com.resync.app.helpers.DatabaseIdHelper;
import com.resync.app.util.Logger;
import com.resync.app.net.RedditRequest;

/* JADX INFO: renamed from: y8.g */
/* JADX INFO: compiled from: OAuthCommentsRequest.java */
/* JADX INFO: loaded from: classes.dex */
public class OAuthCommentsRequest extends RedditRequest<Void> {
    private final String articleId;
    private final String commentId;
    private final boolean clearList;
    private final boolean keepNewCommentFlag;
    ArrayList<String> authorFullnames;

    /* JADX INFO: renamed from: y8.g$a */
    /* JADX INFO: compiled from: OAuthCommentsRequest.java */
    class AuthorInfoResponseListener implements Response.Listener<Void> {

        final /* synthetic */ Void result;

        AuthorInfoResponseListener(Void incomingResult) {
            this.result = incomingResult;
        }

        @Override // com.android.volley.Response.Listener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public void onResponse(Void incomingResult) {
            if (OAuthCommentsRequest.this.responseListener != null) {
                OAuthCommentsRequest.this.responseListener.onResponse(this.result);
            }
        }
    }

    /* JADX INFO: renamed from: y8.g$b */
    /* JADX INFO: compiled from: OAuthCommentsRequest.java */
    class AuthorInfoErrorListener implements Response.ErrorListener {

        final /* synthetic */ Void result;

        AuthorInfoErrorListener(Void incomingResult) {
            this.result = incomingResult;
        }

        @Override // com.android.volley.Response.ErrorListener
        public void onErrorResponse(VolleyError volleyError) {
            if (OAuthCommentsRequest.this.responseListener != null) {
                OAuthCommentsRequest.this.responseListener.onResponse(this.result);
            }
        }
    }

    public OAuthCommentsRequest(Context context, String str, String str2, String str3, String str4, boolean clearList, boolean keepNewCommentFlag, boolean rawJson, Response.Listener<Void> listener, Response.ErrorListener errorListener) {
        super(context, 0, buildUrl(context, str, str2, str3, str4, rawJson), listener, errorListener);
        this.authorFullnames = new ArrayList<>();
        this.articleId = str;
        this.commentId = str3;
        this.subreddit = str2;
        this.clearList = clearList;
        this.keepNewCommentFlag = keepNewCommentFlag;
    }

    private final String subreddit;

    /**
     * Edge-wall fallback: thread JSON is 403-walled for every client now, but the
     * server-rendered thread page carries the whole thread in its DOM (shreddit-post attrs +
     * shreddit-comment elements with depth/author/score/created and a {thingid}-post-rtjson-content
     * body div). The JS rebuilds the legacy [post, comments] listing shape the unchanged
     * CommentParserHelper consumes. Nesting is reconstructed from the depth attribute with a
     * pre-order stack (shreddit renders comments in pre-order, like the old JSON).
     */
    @Override
    protected String[] webProxySpec() {
        StringBuilder page = new StringBuilder("https://www.reddit.com/");
        if (!TextUtils.isEmpty(this.subreddit)) {
            page.append("r/").append(this.subreddit).append("/");
        }
        page.append("comments/").append(this.articleId);
        if (this.commentId != null) {
            page.append("/_/").append(this.commentId);
        }
        page.append("/");
        String js =
            "var p=document.querySelector('shreddit-post');"
          + "if(!p){return '[]';}"
          + "function A(e,n){return e?e.getAttribute(n)||'':'';}"
          + "function N(v){v=parseInt(v,10);return isNaN(v)?0:v;}"
          + "var pid=A(p,'postid').replace(/^t3_/,'');"
          + "var sub=A(p,'subreddit')||'" + (this.subreddit == null ? "" : this.subreddit) + "';"
          + "var t=document.querySelector('shreddit-title');"
          + "var post={id:pid,subreddit:sub,author:A(p,'author')||'[deleted]',"
          + "  title:t?t.innerText:document.title,selftext:'',"
          + "  url:A(p,'content-href')||location.origin+A(p,'permalink'),"
          + "  permalink:A(p,'permalink')||location.pathname,"
          + "  score:N(A(p,'score')),num_comments:N(A(p,'comment-count')),"
          + "  created_utc:Math.round((Date.parse(A(p,'created-timestamp'))||Date.now())/1000),"
          + "  edited:false,gildings:{},domain:'reddit.com',over_18:false,locked:false,"
          + "  stickied:false,hidden:false,archived:false,quarantined:false,saved:false,"
          + "  upvote_ratio:1,visited:false,clicked:false,is_gallery:!!A(p,'gallery'),"
          + "  is_self:false,is_video:false,spoiler:false,link_flair_text:'',author_flair_text:'',"
          + "  thumbnail:'',subreddit_id:'',subreddit_type:'public',subreddit_subscribers:0,"
          + "  view_count:0,likes:null,distinguished:null,media:null,secure_media:null,"
          + "  preview:null,media_embed:{},secure_media_embed:{},author_fullname:'',"
          + "  suggested_sort:'',contest_mode:false,link_flair_background_color:'',"
          + "  link_flair_text_color:'dark',post_hint:'',all_awardings:[],total_awards_received:0};"
          + "var top=[],stack=[],kids={};"
          + "document.querySelectorAll('shreddit-comment').forEach(function(c){"
          + "  var cid=A(c,'thingid').replace(/^t1_/,'');if(!cid){return;}"
          + "  var d=N(A(c,'depth'));"
          + "  var be=document.getElementById(A(c,'thingid')+'-post-rtjson-content')"
          + "    ||document.getElementById(A(c,'thingid')+'-comment-rtjson-content');"
          + "  var body=be?be.innerText.trim():'';"
          + "  var o={kind:'t1',data:{id:cid,author:A(c,'author')||'[deleted]',body:body,"
          + "    selftext:body,url:location.origin+A(c,'permalink'),"
          + "    permalink:A(c,'permalink')||'',"
          + "    parent_id:(d>0&&stack[d-1])?'t1_'+stack[d-1]:'t3_'+pid,"
          + "    score:N(A(c,'score')),ups:N(A(c,'score')),downs:0,"
          + "    created_utc:Math.round(Date.parse(A(c,'created'))/1000)||0,edited:false,"
          + "    gildings:{},replies:{data:{children:[]}},depth:d,is_submitter:false,"
          + "    stickied:false,distinguished:null,author_flair_text:'',controversiality:0,"
          + "    link_id:'t3_'+pid,subreddit:sub,subreddit_id:'',likes:null,collapsed:false,"
          + "    archived:false,locked:false,send_replies:true,can_gild:false,"
          + "    total_awards_received:0,all_awardings:[],awarders:[],treatment_tags:[]}};"
          + "  var arr=(d===0)?top:(kids[stack[d-1]]||(kids[stack[d-1]]=[]));"
          + "  arr.push(o);"
          + "  stack.length=d;stack[d]=cid;"
          + "  kids[cid]=o.data.replies.data.children;"
          + "});"
          + "return JSON.stringify([{data:{children:[{kind:'t3',data:post}]}},"
          + "  {data:{children:top}}]);";
        return new String[]{page.toString(), "shreddit-post", js};
    }

    public static String buildUrl(Context context, String str, String str2, String str3, String str4, boolean rawJson) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(SettingsSingleton.getOauthUrl(context));
        if (!TextUtils.isEmpty(str2)) {
            stringBuffer.append("r/");
            stringBuffer.append(str2);
            stringBuffer.append("/");
        }
        stringBuffer.append("comments/");
        stringBuffer.append(str);
        if (str3 != null) {
            stringBuffer.append("/_/");
            stringBuffer.append(str3);
        }
        stringBuffer.append(".json");
        stringBuffer.append("?always_show_media=1&profile_img=true");
        if (str3 != null) {
            if (!rawJson) {
                stringBuffer.append("&context=100");
            }
        } else if (str4 != null) {
            if (!"suggested".equalsIgnoreCase(str4)) {
                stringBuffer.append("&sort=");
                stringBuffer.append(str4.toLowerCase(Locale.ENGLISH).replaceAll("\\&", ""));
            }
            int commentsCount = SettingsSingleton.getSettings().commentsCount;
            stringBuffer.append("&limit=");
            if (commentsCount == 0) {
                stringBuffer.append("500");
            } else {
                stringBuffer.append(commentsCount);
            }
        }
        return stringBuffer.toString();
    }

    @Override // com.resync.app.net.RedditRequest, com.android.volley.Request
    /* JADX INFO: renamed from: g, reason: merged with bridge method [inline-methods] */
    public void deliverResponse(Void result) {
        if (this.authorFullnames.size() > 0) {
            OAuthRequestManager.sendWithAppContext(new OAuthAuthorInfoRequest(this.context, this.authorFullnames, new AuthorInfoResponseListener(result), new AuthorInfoErrorListener(result)));
        } else {
            super.deliverResponse(result);
        }
    }

    @Override // com.android.volley.Request
    public Response<Void> parseNetworkResponse(NetworkResponse networkResponse) {
        ArrayList topLevelIndices;
        int one;
        try {
            long jCurrentTimeMillis = System.currentTimeMillis();
            ArrayList<ContentValues> arrayListA = CommentParserHelper.parse(this.context, new String(networkResponse.data), 0);
            if (arrayListA.size() > 0) {
                Logger.verbose("Expected comment count: " + arrayListA.get(0).getAsInteger("num_comments").intValue());
            }
            long jCurrentTimeMillis2 = System.currentTimeMillis();
            if (!this.keepNewCommentFlag) {
                for (ContentValues contentValues : arrayListA) {
                    if (contentValues.containsKey("new_comment")) {
                        contentValues.remove("new_comment");
                    }
                }
            }
            int one2 = 1;
            if (arrayListA.size() > 1 && SettingsSingleton.getSettings().commentsCollapseAllDefault && TextUtils.isEmpty(this.commentId)) {
                ArrayList topLevelIndices2 = new ArrayList();
                for (int commentIndex = 1; commentIndex < arrayListA.size(); commentIndex++) {
                    if (arrayListA.get(commentIndex).getAsInteger("level").intValue() == 0) {
                        topLevelIndices2.add(Integer.valueOf(commentIndex));
                    }
                }
                int headerIndex = 0;
                while (headerIndex < topLevelIndices2.size()) {
                    int iIntValue = ((Integer) topLevelIndices2.get(headerIndex)).intValue();
                    int size = arrayListA.size();
                    if (headerIndex < topLevelIndices2.size() - one2) {
                        size = ((Integer) topLevelIndices2.get(headerIndex + 1)).intValue();
                    }
                    if (iIntValue != size) {
                        int descendantIndex = iIntValue + 1;
                        ArrayList arrayList3 = new ArrayList();
                        while (descendantIndex < size) {
                            arrayList3.add(arrayListA.get(descendantIndex).getAsString("_id"));
                            arrayListA.get(descendantIndex).put("collapsed", (Integer) 1);
                            descendantIndex++;
                            one2 = 1;
                            topLevelIndices2 = topLevelIndices2;
                        }
                        topLevelIndices = topLevelIndices2;
                        one = one2;
                        if (arrayList3.size() > 0) {
                            String strJoin = TextUtils.join(",", arrayList3);
                            arrayListA.get(iIntValue).put("childCount", Integer.valueOf(arrayList3.size()));
                            arrayListA.get(iIntValue).put("childIds", strJoin);
                        }
                    } else {
                        topLevelIndices = topLevelIndices2;
                        one = one2;
                    }
                    headerIndex++;
                    one2 = one;
                    topLevelIndices2 = topLevelIndices;
                }
            }
            ContentValues[] contentValuesArrA = ContentValuesListHelper.attachListMetadata(arrayListA, DatabaseIdHelper.buildId(this.articleId, this.commentId), 0);
            if (SettingsSingleton.getSettings().highlightNewAccounts) {
                for (ContentValues contentValues2 : contentValuesArrA) {
                    if (contentValues2.containsKey("post_author_fullname")) {
                        String asString = contentValues2.getAsString("post_author_fullname");
                        if (StringUtils.isNotEmpty(asString) && !this.authorFullnames.contains(asString)) {
                            Logger.verbose("Author: " + asString);
                            this.authorFullnames.add(asString);
                        }
                    }
                }
            }
            long jCurrentTimeMillis3 = System.currentTimeMillis();
            if (!isCanceled()) {
                if (this.clearList) {
                    this.context.getContentResolver().update(RedditProvider.URI_CLEAR, null, DatabaseIdHelper.buildId(this.articleId, this.commentId), null);
                }
                ContentResolver contentResolver = this.context.getContentResolver();
                Uri uri = RedditProvider.URI_MULTIPLE_POSTS;
                Logger.verbose("Inserted: " + contentResolver.bulkInsert(uri, contentValuesArrA));
                this.context.getContentResolver().notifyChange(uri, null);
            }
            long jCurrentTimeMillis4 = System.currentTimeMillis();
            Logger.verbose("Network time: " + networkResponse.networkTimeMs + "\nParsing time: " + (jCurrentTimeMillis2 - jCurrentTimeMillis) + "\nInsertion time: " + (jCurrentTimeMillis4 - jCurrentTimeMillis3) + "\nTotal time: " + (jCurrentTimeMillis4 - jCurrentTimeMillis));
            return Response.success(null, HttpHeaderParser.parseCacheHeaders(networkResponse));
        } catch (JSONException e) {
            return Response.error(new ParseError(e));
        }
    }
}
