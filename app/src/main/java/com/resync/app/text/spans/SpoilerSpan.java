package com.resync.app.text.spans;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.View;
import com.resync.app.helpers.ui.MonetUtil;
import com.resync.app.util.Logger;
import com.resync.app.model.Post;

/* JADX INFO: renamed from: mb.k */
/* JADX INFO: compiled from: SpoilerSpan.java */
/* JADX INFO: loaded from: classes3.dex */
public class SpoilerSpan extends CharacterStyle implements UpdateAppearance, CustomClickableSpan {

    private final Post post;
    private final int spoilerIndex;
    private final int spoilerColor = MonetUtil.getLinkColorAuto();

    // Opened by a tap. Sync used to reveal a spoiler by rewriting the markers out of the body it
    // held for the post, but by the time the span is tapped those markers are already gone from
    // that body, so the tap ended at "Missing post information" instead. Revealing in place is
    // the whole fix: a spoiler is hidden only by being painted in the colour it sits on, and the
    // span changes nothing but how it is drawn, so drawing again without the paint shows the
    // words that are already there.
    private boolean revealed;

    public SpoilerSpan(Post post, int spoilerIndex) {
        this.post = post;
        this.spoilerIndex = spoilerIndex + 1;
    }

    public int getSpoilerColor() {
        return this.spoilerColor;
    }

    @Override // com.resync.app.text.spans.CustomClickableSpan
    public void onClick(View view) {
        Logger.debug("Opened a spoiler");
        this.revealed = true;
        if (view != null) {
            view.invalidate();
        }
    }

    @Override // com.resync.app.text.spans.CustomClickableSpan
    public void onLongClick(View view) {
    }

    @Override // android.text.style.CharacterStyle
    public void updateDrawState(TextPaint textPaint) {
        if (this.revealed) {
            return;
        }
        textPaint.bgColor = this.spoilerColor;
        textPaint.setColor(getSpoilerColor());
    }
}
