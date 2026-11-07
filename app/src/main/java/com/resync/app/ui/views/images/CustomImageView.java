package com.resync.app.ui.views.images;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.VectorDrawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import androidx.appcompat.widget.AppCompatImageView;
import com.bumptech.glide.Priority;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.target.ViewTarget;
import androidx.palette.graphics.Palette;
import com.resync.app.RedditApplication;
import com.resync.app.singleton.SettingsSingleton;
import com.resync.app.ui.views.core.SubView;
import com.resync.app.internal.SyncStyleable;
import com.resync.app.widgets.RecyclableView;
import com.resync.app.widgets.CircularShapeOverlay;
import com.resync.app.widgets.RoundedCornersShapeOverlay;
import com.resync.app.widgets.ShapeOverlay;
import com.resync.app.widgets.ColorFilterProvider;
import com.resync.app.helpers.NightHelper;
import com.resync.app.util.Logger;

/* JADX INFO: loaded from: classes3.dex */
public class CustomImageView extends AppCompatImageView implements RecyclableView {

    private boolean blur;

    private boolean dimmable;

    private boolean nsfw;
    protected ViewTarget<ImageView, Drawable> requestTarget;

    private ShapeOverlay shapeOverlay;
    private boolean disableCrossFade;
    private int overlayColor;

    class ImageRequestListener implements RequestListener<Drawable> {
        ImageRequestListener() {
        }

        @Override // com.bumptech.glide.request.RequestListener
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public boolean onResourceReady(Drawable drawable, Object obj, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            CustomImageView.this.onImageReady();
            return false;
        }

        @Override // com.bumptech.glide.request.RequestListener
        public boolean onLoadFailed(GlideException glideException, Object obj, Target<Drawable> target, boolean isFirstResource) {
            return false;
        }
    }

    public CustomImageView(Context context) {
        super(context);
    }

    private void useRoundedCorners(int radius) {
        this.shapeOverlay = new RoundedCornersShapeOverlay(radius);
    }

    public static int resolveOverlayColor(boolean dim, boolean blur) {
        if (blur) {
            return -7859180;
        }
        if (!dim) {
            return 0;
        }
        if (NightHelper.isNight()) {
            return NightHelper.isNight() ? 1998331424 : 0;
        }
        return -1996488705;
    }

    private void useCircularShape(int radius) {
        this.shapeOverlay = new CircularShapeOverlay(radius);
    }

    private void applyColorFilterToDrawable() {
        applyColorFilter(getDrawable());
    }

    private void applyColorFilter(Drawable drawable) {
        ColorFilter colorFilter;
        if (drawable == null || (colorFilter = getColorFilter()) == null) {
            return;
        }
        drawable.setColorFilter(colorFilter);
    }

    public void setNsfw(boolean nsfw) {
        this.nsfw = nsfw;
    }

    public void setOverlayColor(int overlayColor) {
        this.overlayColor = overlayColor;
    }

    protected void setDimmable(boolean dimmable) {
        this.dimmable = dimmable;
    }

    public Bitmap extractBitmap(TransitionDrawable transitionDrawable) {
        for (int layerIndex = 0; layerIndex < transitionDrawable.getNumberOfLayers(); layerIndex++) {
            Logger.verbose("DOM COLOR: " + transitionDrawable.getDrawable(layerIndex));
            if (transitionDrawable.getDrawable(layerIndex) instanceof BitmapDrawable) {
                return ((BitmapDrawable) transitionDrawable.getDrawable(layerIndex)).getBitmap();
            }
        }
        return null;
    }

    @Override // com.resync.app.widgets.RecyclableView
    public final void recycle() {
        Logger.verbose("Recycling: " + getClass().getSimpleName());
        if (getDrawable() instanceof GifDrawable) {
            ((GifDrawable) getDrawable()).stop();
        }
        setImageDrawable(null);
        if (this.requestTarget != null) {
            Glide.with(RedditApplication.getAppContext()).clear(this.requestTarget);
            this.requestTarget = null;
        }
        onRecycled();
    }

    @Override // android.widget.ImageView
    public ColorFilter getColorFilter() {
        int overlayColor = resolveOverlayColor(!isNsfw() && shouldDim(), !isNsfw() && isBlur());
        Logger.verbose("Color: " + overlayColor);
        if (overlayColor == 0) {
            return null;
        }
        return ColorFilterProvider.getColorFilter(overlayColor);
    }

    public int getDominantColor() {
        Logger.verbose("DOM COLOR: " + getDrawable());
        if (getDrawable() instanceof TransitionDrawable) {
            return Palette.from(extractBitmap((TransitionDrawable) getDrawable())).generate().getDominantColor(-16777216);
        }
        if (getDrawable() instanceof BitmapDrawable) {
            return Palette.from(((BitmapDrawable) getDrawable()).getBitmap()).generate().getDominantColor(-16777216);
        }
        return -16777216;
    }

    public boolean isBlur() {
        return this.blur;
    }

    public boolean isNsfw() {
        return this.nsfw;
    }

    public int getOverlayColor() {
        return this.overlayColor;
    }

    public boolean shouldDim() {
        return this.dimmable && SettingsSingleton.getSettings().dimImages;
    }

    public boolean hasDrawable() {
        if (!(getDrawable() instanceof BitmapDrawable)) {
            return getDrawable() instanceof TransitionDrawable;
        }
        BitmapDrawable bitmapDrawable = (BitmapDrawable) getDrawable();
        return (bitmapDrawable.getBitmap() == null || bitmapDrawable.getBitmap() == null) ? false : true;
    }

    public boolean hasValidBitmap() {
        return (!(getDrawable() instanceof BitmapDrawable) || ((BitmapDrawable) getDrawable()).getBitmap() == null || ((BitmapDrawable) getDrawable()).getBitmap().isRecycled()) ? false : true;
    }

    public void load(String url) {
        load(url, false, false, -1, -1);
    }

    @Override // android.widget.ImageView, android.view.View
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!(getDrawable() instanceof GifDrawable) || ((GifDrawable) getDrawable()).isRunning()) {
            return;
        }
        try {
            ((GifDrawable) getDrawable()).start();
        } catch (Exception e) {
            Logger.logException(e, false);
        }
    }

    @Override // android.widget.ImageView, android.view.View
    protected void onDetachedFromWindow() {
        if (getDrawable() instanceof GifDrawable) {
            ((GifDrawable) getDrawable()).stop();
        }
        super.onDetachedFromWindow();
    }

    @Override // android.widget.ImageView, android.view.View
    protected void onDraw(Canvas canvas) {
        if (hasValidBitmap() || (getDrawable() instanceof TransitionDrawable) || (getDrawable() instanceof VectorDrawable) || (getDrawable() instanceof GifDrawable)) {
            super.onDraw(canvas);
        }
        ShapeOverlay shapeOverlay = this.shapeOverlay;
        if (shapeOverlay != null) {
            shapeOverlay.draw(canvas);
        }
    }

    @Override // android.view.View
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        ShapeOverlay shapeOverlay = this.shapeOverlay;
        if (shapeOverlay != null) {
            shapeOverlay.update(width, height, oldWidth, oldHeight);
        }
    }

    public void load(String url, boolean dim, boolean blur) {
        load(url, dim, blur, -1, -1);
    }

    @Override // android.view.View
    public boolean postDelayed(Runnable runnable, long delayMillis) {
        return runnable.getClass().getSimpleName().equals("CheckForLongPress") ? super.postDelayed(runnable, delayMillis / 2) : super.postDelayed(runnable, delayMillis);
    }

    public void load(String url, boolean dim, boolean blur, int width, int height) {
        load(url, dim, blur, width, height, null, false);
    }

    public void load(String url, boolean dim, boolean blur, int width, int height, ImageView.ScaleType scaleType, boolean crossFade) {
        configure(dim, blur);
        if (hasDrawable()) {
            return;
        }
        RequestBuilder<Drawable> requestBuilder = Glide.with(RedditApplication.getAppContext()).load(url);
        if (width > 0 && height > 0) {
            requestBuilder = (RequestBuilder) requestBuilder.override(width, height);
        }
        if (scaleType == ImageView.ScaleType.FIT_CENTER) {
            requestBuilder = (RequestBuilder) requestBuilder.fitCenter();
        } else if (scaleType == ImageView.ScaleType.CENTER_CROP) {
            requestBuilder = (RequestBuilder) requestBuilder.centerCrop();
        }
        if (crossFade && !this.disableCrossFade) {
            requestBuilder = requestBuilder.transition(DrawableTransitionOptions.withCrossFade(220));
        }
        this.requestTarget = requestBuilder.listener(new ImageRequestListener()).priority(Priority.IMMEDIATE).into(this);
    }

    protected void onImageReady() {
    }

    @Override // android.widget.ImageView
    public void setColorFilter(ColorFilter colorFilter) {
        if (getDrawable() != null) {
            getDrawable().setColorFilter(colorFilter);
        }
    }

    @Override // androidx.appcompat.widget.AppCompatImageView, android.widget.ImageView
    public void setImageDrawable(Drawable drawable) {
        if ((drawable instanceof TransitionDrawable) && !(this instanceof SubView) && !isShown()) {
            Logger.verbose("Skipped animation");
            ((TransitionDrawable) drawable).startTransition(0);
        }
        applyColorFilter(drawable);
        super.setImageDrawable(drawable);
    }

    protected void onRecycled() {
    }

    public void disableCrossFade() {
        this.disableCrossFade = true;
    }

    protected void setBlur(boolean blur) {
        this.blur = blur;
    }

    public void configure(boolean dim, boolean blur) {
        setDimmable(dim);
        setBlur(blur);
        applyColorFilterToDrawable();
    }

    public CustomImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        TypedArray typedArrayObtainStyledAttributes = context.obtainStyledAttributes(attributeSet, SyncStyleable.SHAPED_IMAGE_VIEW);
        boolean roundedCorners = typedArrayObtainStyledAttributes.getBoolean(1, false);
        boolean circular = typedArrayObtainStyledAttributes.getBoolean(2, false);
        int radius = typedArrayObtainStyledAttributes.getInt(0, 2);
        typedArrayObtainStyledAttributes.recycle();
        if (roundedCorners && circular) {
            throw new RuntimeException("Cannot have rounded corners and circle shape");
        }
        if (circular) {
            useRoundedCorners(radius);
        } else if (roundedCorners) {
            useCircularShape(radius);
        }
    }
}
