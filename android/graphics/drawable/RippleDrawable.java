/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics.drawable;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;

import com.android.internal.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;

/**
 * Drawable that shows a ripple effect in response to state changes. The
 * anchoring position of the ripple for a given state may be specified by
 * calling {@link #setHotspot(float, float)} with the corresponding state
 * attribute identifier.
 * <p>
 * A touch feedback drawable may contain multiple child layers, including a
 * special mask layer that is not drawn to the screen. A single layer may be set
 * as the mask by specifying its android:id value as {@link android.R.id#mask}.
 * <pre>
 * <code>&lt!-- A red ripple masked against an opaque rectangle. --/>
 * &ltripple android:color="#ffff0000">
 *   &ltitem android:id="@android:id/mask"
 *         android:drawable="@android:color/white" />
 * &ltripple /></code>
 * </pre>
 * <p>
 * If a mask layer is set, the ripple effect will be masked against that layer
 * before it is drawn over the composite of the remaining child layers.
 * <p>
 * If no mask layer is set, the ripple effect is masked against the composite
 * of the child layers.
 * <pre>
 * <code>&lt!-- A blue ripple drawn atop a black rectangle. --/>
 * &ltripple android:color="#ff00ff00">
 *   &ltitem android:drawable="@android:color/black" />
 * &ltripple />
 *
 * &lt!-- A red ripple drawn atop a drawable resource. --/>
 * &ltripple android:color="#ff00ff00">
 *   &ltitem android:drawable="@drawable/my_drawable" />
 * &ltripple /></code>
 * </pre>
 * <p>
 * If no child layers or mask is specified and the ripple is set as a View
 * background, the ripple will be drawn atop the first available parent
 * background within the View's hierarchy. In this case, the drawing region
 * may extend outside of the Drawable bounds.
 * <pre>
 * <code>&lt!-- An unbounded green ripple. --/>
 * &ltripple android:color="#ff0000ff" /></code>
 * </pre>
 *
 * @attr ref android.R.styleable#RippleDrawable_color
 */
public class RippleDrawable extends LayerDrawable {
    private static final PorterDuffXfermode DST_IN = new PorterDuffXfermode(Mode.DST_IN);
    private static final PorterDuffXfermode SRC_ATOP = new PorterDuffXfermode(Mode.SRC_ATOP);
    private static final PorterDuffXfermode SRC_OVER = new PorterDuffXfermode(Mode.SRC_OVER);

    /**
     * Constant for automatically determining the maximum ripple radius.
     *
     * @see #setMaxRadius(int)
     * @hide
     */
    public static final int RADIUS_AUTO = -1;

    /** The maximum number of ripples supported. */
    private static final int MAX_RIPPLES = 10;

    private final Rect mTempRect = new Rect();

    /** Current ripple effect bounds, used to constrain ripple effects. */
    private final Rect mHotspotBounds = new Rect();

    /** Current drawing bounds, used to compute dirty region. */
    private final Rect mDrawingBounds = new Rect();

    /** Current dirty bounds, union of current and previous drawing bounds. */
    private final Rect mDirtyBounds = new Rect();

    /** Mirrors mLayerState with some extra information. */
    private RippleState mState;

    /** The masking layer, e.g. the layer with id R.id.mask. */
    private Drawable mMask;

    /** The current background. May be actively animating or pending entry. */
    private RippleBackground mBackground;

    /** Whether we expect to draw a background when visible. */
    private boolean mBackgroundActive;

    /** The current ripple. May be actively animating or pending entry. */
    private Ripple mRipple;

    /** Whether we expect to draw a ripple when visible. */
    private boolean mRippleActive;

    // Hotspot coordinates that are awaiting activation.
    private float mPendingX;
    private float mPendingY;
    private boolean mHasPending;

    /**
     * Lazily-created array of actively animating ripples. Inactive ripples are
     * pruned during draw(). The locations of these will not change.
     */
    private Ripple[] mExitingRipples;
    private int mExitingRipplesCount = 0;

    /** Paint used to control appearance of ripples. */
    private Paint mRipplePaint;

    /** Paint used to control reveal layer masking. */
    private Paint mMaskingPaint;

    /** Target density of the display into which ripples are drawn. */
    private float mDensity = 1.0f;

    /** Whether bounds are being overridden. */
    private boolean mOverrideBounds;

    /**
     * Whether the next draw MUST draw something to canvas. Used to work around
     * a bug in hardware invalidation following a render thread-accelerated
     * animation.
     */
    private boolean mNeedsDraw;

    /**
     * Constructor used for drawable inflation.
     */
    RippleDrawable() {
        this(new RippleState(null, null, null), null, null);
    }

    /**
     * Creates a new ripple drawable with the specified ripple color and
     * optional content and mask drawables.
     *
     * @param color The ripple color
     * @param content The content drawable, may be {@code null}
     * @param mask The mask drawable, may be {@code null}
     */
    public RippleDrawable(@NonNull ColorStateList color, @Nullable Drawable content,
            @Nullable Drawable mask) {
        this(new RippleState(null, null, null), null, null);

        if (color == null) {
            throw new IllegalArgumentException("RippleDrawable requires a non-null color");
        }

        if (content != null) {
            addLayer(content, null, 0, 0, 0, 0, 0);
        }

        if (mask != null) {
            addLayer(mask, null, android.R.id.mask, 0, 0, 0, 0);
        }

        setColor(color);
        ensurePadding();
        initializeFromState();
    }

    @Override
    public void jumpToCurrentState() {
        super.jumpToCurrentState();

        boolean needsDraw = false;

        if (mRipple != null) {
            needsDraw |= mRipple.isHardwareAnimating();
            mRipple.jump();
        }

        if (mBackground != null) {
            needsDraw |= mBackground.isHardwareAnimating();
            mBackground.jump();
        }

        needsDraw |= cancelExitingRipples();

        mNeedsDraw = needsDraw;
        invalidateSelf();
    }

    private boolean cancelExitingRipples() {
        boolean needsDraw = false;

        final int count = mExitingRipplesCount;
        final Ripple[] ripples = mExitingRipples;
        for (int i = 0; i < count; i++) {
            needsDraw |= ripples[i].isHardwareAnimating();
            ripples[i].cancel();
        }

        if (ripples != null) {
            Arrays.fill(ripples, 0, count, null);
        }
        mExitingRipplesCount = 0;

        return needsDraw;
    }

    @Override
    public void setAlpha(int alpha) {
        super.setAlpha(alpha);

        // TODO: Should we support this?
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        super.setColorFilter(cf);

        // TODO: Should we support this?
    }

    @Override
    public int getOpacity() {
        // Worst-case scenario.
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        final boolean changed = super.onStateChange(stateSet);

        boolean enabled = false;
        boolean pressed = false;
        boolean focused = false;

        for (int state : stateSet) {
            if (state == R.attr.state_enabled) {
                enabled = true;
            }
            if (state == R.attr.state_focused) {
                focused = true;
            }
            if (state == R.attr.state_pressed) {
                pressed = true;
            }
        }

        setRippleActive(enabled && pressed);
        setBackgroundActive(focused || (enabled && pressed));

        return changed;
    }

    private void setRippleActive(boolean active) {
        if (mRippleActive != active) {
            mRippleActive = active;
            if (active) {
                tryRippleEnter();
            } else {
                tryRippleExit();
            }
        }
    }

    private void setBackgroundActive(boolean active) {
        if (mBackgroundActive != active) {
            mBackgroundActive = active;
            if (active) {
                tryBackgroundEnter();
            } else {
                tryBackgroundExit();
            }
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        if (!mOverrideBounds) {
            mHotspotBounds.set(bounds);
            onHotspotBoundsChanged();
        }

        invalidateSelf();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        final boolean changed = super.setVisible(visible, restart);

        if (!visible) {
            clearHotspots();
        } else if (changed) {
            // If we just became visible, ensure the background and ripple
            // visibilities are consistent with their internal states.
            if (mRippleActive) {
                tryRippleEnter();
            }

            if (mBackgroundActive) {
                tryBackgroundEnter();
            }
        }

        return changed;
    }

    /**
     * @hide
     */
    @Override
    public boolean isProjected() {
        return getNumberOfLayers() == 0;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    public void setColor(ColorStateList color) {
        mState.mColor = color;
        invalidateSelf();
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
            throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.RippleDrawable);
        updateStateFromTypedArray(a);
        a.recycle();

        // Force padding default to STACK before inflating.
        setPaddingMode(PADDING_MODE_STACK);

        super.inflate(r, parser, attrs, theme);

        setTargetDensity(r.getDisplayMetrics());
        initializeFromState();
    }

    @Override
    public boolean setDrawableByLayerId(int id, Drawable drawable) {
        if (super.setDrawableByLayerId(id, drawable)) {
            if (id == R.id.mask) {
                mMask = drawable;
            }

            return true;
        }

        return false;
    }

    /**
     * Specifies how layer padding should affect the bounds of subsequent
     * layers. The default and recommended value for RippleDrawable is
     * {@link #PADDING_MODE_STACK}.
     *
     * @param mode padding mode, one of:
     *            <ul>
     *            <li>{@link #PADDING_MODE_NEST} to nest each layer inside the
     *            padding of the previous layer
     *            <li>{@link #PADDING_MODE_STACK} to stack each layer directly
     *            atop the previous layer
     *            </ul>
     * @see #getPaddingMode()
     */
    @Override
    public void setPaddingMode(int mode) {
        super.setPaddingMode(mode);
    }

    /**
     * Initializes the constant state from the values in the typed array.
     */
    private void updateStateFromTypedArray(TypedArray a) throws XmlPullParserException {
        final RippleState state = mState;

        // Account for any configuration changes.
        state.mChangingConfigurations |= a.getChangingConfigurations();

        // Extract the theme attributes, if any.
        state.mTouchThemeAttrs = a.extractThemeAttrs();

        final ColorStateList color = a.getColorStateList(R.styleable.RippleDrawable_color);
        if (color != null) {
            mState.mColor = color;
        }

        verifyRequiredAttributes(a);
    }

    private void verifyRequiredAttributes(TypedArray a) throws XmlPullParserException {
        if (mState.mColor == null && (mState.mTouchThemeAttrs == null
                || mState.mTouchThemeAttrs[R.styleable.RippleDrawable_color] == 0)) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    ": <ripple> requires a valid color attribute");
        }
    }

    /**
     * Set the density at which this drawable will be rendered.
     *
     * @param metrics The display metrics for this drawable.
     */
    private void setTargetDensity(DisplayMetrics metrics) {
        if (mDensity != metrics.density) {
            mDensity = metrics.density;
            invalidateSelf();
        }
    }

    @Override
    public void applyTheme(Theme t) {
        super.applyTheme(t);

        final RippleState state = mState;
        if (state == null || state.mTouchThemeAttrs == null) {
            return;
        }

        final TypedArray a = t.resolveAttributes(state.mTouchThemeAttrs,
                R.styleable.RippleDrawable);
        try {
            updateStateFromTypedArray(a);
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        } finally {
            a.recycle();
        }

        initializeFromState();
    }

    @Override
    public boolean canApplyTheme() {
        return super.canApplyTheme() || mState != null && mState.mTouchThemeAttrs != null;
    }

    @Override
    public void setHotspot(float x, float y) {
        if (mRipple == null || mBackground == null) {
            mPendingX = x;
            mPendingY = y;
            mHasPending = true;
        }

        if (mRipple != null) {
            mRipple.move(x, y);
        }
    }

    /**
     * Creates an active hotspot at the specified location.
     */
    private void tryBackgroundEnter() {
        if (mBackground == null) {
            mBackground = new RippleBackground(this, mHotspotBounds);
        }

        final int color = mState.mColor.getColorForState(getState(), Color.TRANSPARENT);
        mBackground.setup(mState.mMaxRadius, color, mDensity);
        mBackground.enter();
    }

    private void tryBackgroundExit() {
        if (mBackground != null) {
            // Don't null out the background, we need it to draw!
            mBackground.exit();
        }
    }

    /**
     * Attempts to start an enter animation for the active hotspot. Fails if
     * there are too many animating ripples.
     */
    private void tryRippleEnter() {
        if (mExitingRipplesCount >= MAX_RIPPLES) {
            // This should never happen unless the user is tapping like a maniac
            // or there is a bug that's preventing ripples from being removed.
            return;
        }

        if (mRipple == null) {
            final float x;
            final float y;
            if (mHasPending) {
                mHasPending = false;
                x = mPendingX;
                y = mPendingY;
            } else {
                x = mHotspotBounds.exactCenterX();
                y = mHotspotBounds.exactCenterY();
            }
            mRipple = new Ripple(this, mHotspotBounds, x, y);
        }

        final int color = mState.mColor.getColorForState(getState(), Color.TRANSPARENT);
        mRipple.setup(mState.mMaxRadius, color, mDensity);
        mRipple.enter();
    }

    /**
     * Attempts to start an exit animation for the active hotspot. Fails if
     * there is no active hotspot.
     */
    private void tryRippleExit() {
        if (mRipple != null) {
            if (mExitingRipples == null) {
                mExitingRipples = new Ripple[MAX_RIPPLES];
            }
            mExitingRipples[mExitingRipplesCount++] = mRipple;
            mRipple.exit();
            mRipple = null;
        }
    }

    /**
     * Cancels and removes the active ripple, all exiting ripples, and the
     * background. Nothing will be drawn after this method is called.
     */
    private void clearHotspots() {
        boolean needsDraw = false;

        if (mRipple != null) {
            needsDraw |= mRipple.isHardwareAnimating();
            mRipple.cancel();
            mRipple = null;
        }

        if (mBackground != null) {
            needsDraw |= mBackground.isHardwareAnimating();
            mBackground.cancel();
            mBackground = null;
        }

        needsDraw |= cancelExitingRipples();

        mNeedsDraw = needsDraw;
        invalidateSelf();
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        mOverrideBounds = true;
        mHotspotBounds.set(left, top, right, bottom);

        onHotspotBoundsChanged();
    }

    /** @hide */
    @Override
    public void getHotspotBounds(Rect outRect) {
        outRect.set(mHotspotBounds);
    }

    /**
     * Notifies all the animating ripples that the hotspot bounds have changed.
     */
    private void onHotspotBoundsChanged() {
        final int count = mExitingRipplesCount;
        final Ripple[] ripples = mExitingRipples;
        for (int i = 0; i < count; i++) {
            ripples[i].onHotspotBoundsChanged();
        }

        if (mRipple != null) {
            mRipple.onHotspotBoundsChanged();
        }

        if (mBackground != null) {
            mBackground.onHotspotBoundsChanged();
        }
    }

    /**
     * Populates <code>outline</code> with the first available layer outline,
     * excluding the mask layer.
     *
     * @param outline Outline in which to place the first available layer outline
     */
    @Override
    public void getOutline(@NonNull Outline outline) {
        final LayerState state = mLayerState;
        final ChildDrawable[] children = state.mChildren;
        final int N = state.mNum;
        for (int i = 0; i < N; i++) {
            if (children[i].mId != R.id.mask) {
                children[i].mDrawable.getOutline(outline);
                if (!outline.isEmpty()) return;
            }
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final boolean hasMask = mMask != null;
        final boolean drawNonMaskContent = mLayerState.mNum > (hasMask ? 1 : 0);
        final boolean drawMask = hasMask && mMask.getOpacity() != PixelFormat.OPAQUE;
        final Rect bounds = getDirtyBounds();
        final int saveCount = canvas.save(Canvas.CLIP_SAVE_FLAG);
        canvas.clipRect(bounds);

        // If we have content, draw it into a layer first.
        final int contentLayer;
        if (drawNonMaskContent) {
            contentLayer = drawContentLayer(canvas, bounds, SRC_OVER);
        } else {
            contentLayer = -1;
        }

        // Next, try to draw the ripples (into a layer if necessary). If we need
        // to mask against the underlying content, set the xfermode to SRC_ATOP.
        final PorterDuffXfermode xfermode = (hasMask || !drawNonMaskContent) ? SRC_OVER : SRC_ATOP;

        // If we have a background and a non-opaque mask, draw the masking layer.
        final int backgroundLayer = drawBackgroundLayer(canvas, bounds, xfermode, drawMask);
        if (backgroundLayer >= 0) {
            if (drawMask) {
                drawMaskingLayer(canvas, bounds, DST_IN);
            }
            canvas.restoreToCount(backgroundLayer);
        }

        // If we have ripples and a non-opaque mask, draw the masking layer.
        final int rippleLayer = drawRippleLayer(canvas, bounds, xfermode);
        if (rippleLayer >= 0) {
            if (drawMask) {
                drawMaskingLayer(canvas, bounds, DST_IN);
            }
            canvas.restoreToCount(rippleLayer);
        }

        // If we failed to draw anything and we just canceled animations, at
        // least draw a color so that hardware invalidation works correctly.
        if (contentLayer < 0 && backgroundLayer < 0 && rippleLayer < 0 && mNeedsDraw) {
            canvas.drawColor(Color.TRANSPARENT);

            // Request another draw so we can avoid adding a transparent layer
            // during the next display list refresh.
            invalidateSelf();
        }
        mNeedsDraw = false;

        canvas.restoreToCount(saveCount);
    }

    /**
     * Removes a ripple from the exiting ripple list.
     *
     * @param ripple the ripple to remove
     */
    void removeRipple(Ripple ripple) {
        // Ripple ripple ripple ripple. Ripple ripple.
        final Ripple[] ripples = mExitingRipples;
        final int count = mExitingRipplesCount;
        final int index = getRippleIndex(ripple);
        if (index >= 0) {
            System.arraycopy(ripples, index + 1, ripples, index, count - (index + 1));
            ripples[count - 1] = null;
            mExitingRipplesCount--;

            invalidateSelf();
        }
    }

    private int getRippleIndex(Ripple ripple) {
        final Ripple[] ripples = mExitingRipples;
        final int count = mExitingRipplesCount;
        for (int i = 0; i < count; i++) {
            if (ripples[i] == ripple) {
                return i;
            }
        }
        return -1;
    }

    private int drawContentLayer(Canvas canvas, Rect bounds, PorterDuffXfermode mode) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int count = mLayerState.mNum;

        // We don't need a layer if we don't expect to draw any ripples, we have
        // an explicit mask, or if the non-mask content is all opaque.
        boolean needsLayer = false;
        if ((mExitingRipplesCount > 0 || mBackground != null) && mMask == null) {
            for (int i = 0; i < count; i++) {
                if (array[i].mId != R.id.mask
                        && array[i].mDrawable.getOpacity() != PixelFormat.OPAQUE) {
                    needsLayer = true;
                    break;
                }
            }
        }

        final Paint maskingPaint = getMaskingPaint(mode);
        final int restoreToCount = needsLayer ? canvas.saveLayer(bounds.left, bounds.top,
                bounds.right, bounds.bottom, maskingPaint) : -1;

        // Draw everything except the mask.
        for (int i = 0; i < count; i++) {
            if (array[i].mId != R.id.mask) {
                array[i].mDrawable.draw(canvas);
            }
        }

        return restoreToCount;
    }

    private int drawBackgroundLayer(
            Canvas canvas, Rect bounds, PorterDuffXfermode mode, boolean drawMask) {
        int saveCount = -1;

        if (mBackground != null && mBackground.shouldDraw()) {
            // TODO: We can avoid saveLayer here if we push the xfermode into
            // the background's render thread animator at exit() time.
            if (drawMask || mode != SRC_OVER) {
                saveCount = canvas.saveLayer(bounds.left, bounds.top, bounds.right,
                        bounds.bottom, getMaskingPaint(mode));
            }

            final float x = mHotspotBounds.exactCenterX();
            final float y = mHotspotBounds.exactCenterY();
            canvas.translate(x, y);
            mBackground.draw(canvas, getRipplePaint());
            canvas.translate(-x, -y);
        }

        return saveCount;
    }

    private int drawRippleLayer(Canvas canvas, Rect bounds, PorterDuffXfermode mode) {
        boolean drewRipples = false;
        int restoreToCount = -1;
        int restoreTranslate = -1;

        // Draw ripples and update the animating ripples array.
        final int count = mExitingRipplesCount;
        final Ripple[] ripples = mExitingRipples;
        for (int i = 0; i <= count; i++) {
            final Ripple ripple;
            if (i < count) {
                ripple = ripples[i];
            } else if (mRipple != null) {
                ripple = mRipple;
            } else {
                continue;
            }

            // If we're masking the ripple layer, make sure we have a layer
            // first. This will merge SRC_OVER (directly) onto the canvas.
            if (restoreToCount < 0) {
                final Paint maskingPaint = getMaskingPaint(mode);
                final int color = mState.mColor.getColorForState(getState(), Color.TRANSPARENT);
                final int alpha = Color.alpha(color);
                maskingPaint.setAlpha(alpha / 2);

                // TODO: We can avoid saveLayer here if we're only drawing one
                // ripple and we don't have content or a translucent mask.
                restoreToCount = canvas.saveLayer(bounds.left, bounds.top,
                        bounds.right, bounds.bottom, maskingPaint);

                // Translate the canvas to the current hotspot bounds.
                restoreTranslate = canvas.save();
                canvas.translate(mHotspotBounds.exactCenterX(), mHotspotBounds.exactCenterY());
            }

            drewRipples |= ripple.draw(canvas, getRipplePaint());
        }

        // Always restore the translation.
        if (restoreTranslate >= 0) {
            canvas.restoreToCount(restoreTranslate);
        }

        // If we created a layer with no content, merge it immediately.
        if (restoreToCount >= 0 && !drewRipples) {
            canvas.restoreToCount(restoreToCount);
            restoreToCount = -1;
        }

        return restoreToCount;
    }

    private int drawMaskingLayer(Canvas canvas, Rect bounds, PorterDuffXfermode mode) {
        final int restoreToCount = canvas.saveLayer(bounds.left, bounds.top,
                bounds.right, bounds.bottom, getMaskingPaint(mode));

        // Ensure that DST_IN blends using the entire layer.
        canvas.drawColor(Color.TRANSPARENT);

        mMask.draw(canvas);

        return restoreToCount;
    }

    private Paint getRipplePaint() {
        if (mRipplePaint == null) {
            mRipplePaint = new Paint();
            mRipplePaint.setAntiAlias(true);
        }
        return mRipplePaint;
    }

    private Paint getMaskingPaint(PorterDuffXfermode xfermode) {
        if (mMaskingPaint == null) {
            mMaskingPaint = new Paint();
        }
        mMaskingPaint.setXfermode(xfermode);
        mMaskingPaint.setAlpha(0xFF);
        return mMaskingPaint;
    }

    @Override
    public Rect getDirtyBounds() {
        if (isProjected()) {
            final Rect drawingBounds = mDrawingBounds;
            final Rect dirtyBounds = mDirtyBounds;
            dirtyBounds.set(drawingBounds);
            drawingBounds.setEmpty();

            final int cX = (int) mHotspotBounds.exactCenterX();
            final int cY = (int) mHotspotBounds.exactCenterY();
            final Rect rippleBounds = mTempRect;

            final Ripple[] activeRipples = mExitingRipples;
            final int N = mExitingRipplesCount;
            for (int i = 0; i < N; i++) {
                activeRipples[i].getBounds(rippleBounds);
                rippleBounds.offset(cX, cY);
                drawingBounds.union(rippleBounds);
            }

            final RippleBackground background = mBackground;
            if (background != null) {
                background.getBounds(rippleBounds);
                rippleBounds.offset(cX, cY);
                drawingBounds.union(rippleBounds);
            }

            dirtyBounds.union(drawingBounds);
            dirtyBounds.union(super.getDirtyBounds());
            return dirtyBounds;
        } else {
            return getBounds();
        }
    }

    @Override
    public ConstantState getConstantState() {
        return mState;
    }

    @Override
    public Drawable mutate() {
        super.mutate();

        // LayerDrawable creates a new state using createConstantState, so
        // this should always be a safe cast.
        mState = (RippleState) mLayerState;
        return this;
    }

    @Override
    RippleState createConstantState(LayerState state, Resources res) {
        return new RippleState(state, this, res);
    }

    static class RippleState extends LayerState {
        int[] mTouchThemeAttrs;
        ColorStateList mColor = ColorStateList.valueOf(Color.MAGENTA);
        int mMaxRadius = RADIUS_AUTO;

        public RippleState(LayerState orig, RippleDrawable owner, Resources res) {
            super(orig, owner, res);

            if (orig != null && orig instanceof RippleState) {
                final RippleState origs = (RippleState) orig;
                mTouchThemeAttrs = origs.mTouchThemeAttrs;
                mColor = origs.mColor;
                mMaxRadius = origs.mMaxRadius;
            }
        }

        @Override
        public boolean canApplyTheme() {
            return mTouchThemeAttrs != null || super.canApplyTheme();
        }

        @Override
        public Drawable newDrawable() {
            return new RippleDrawable(this, null, null);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new RippleDrawable(this, res, null);
        }

        @Override
        public Drawable newDrawable(Resources res, Theme theme) {
            return new RippleDrawable(this, res, theme);
        }
    }

    /**
     * Sets the maximum ripple radius in pixels. The default value of
     * {@link #RADIUS_AUTO} defines the radius as the distance from the center
     * of the drawable bounds (or hotspot bounds, if specified) to a corner.
     *
     * @param maxRadius the maximum ripple radius in pixels or
     *            {@link #RADIUS_AUTO} to automatically determine the maximum
     *            radius based on the bounds
     * @see #getMaxRadius()
     * @see #setHotspotBounds(int, int, int, int)
     * @hide
     */
    public void setMaxRadius(int maxRadius) {
        if (maxRadius != RADIUS_AUTO && maxRadius < 0) {
            throw new IllegalArgumentException("maxRadius must be RADIUS_AUTO or >= 0");
        }

        mState.mMaxRadius = maxRadius;
    }

    /**
     * @return the maximum ripple radius in pixels, or {@link #RADIUS_AUTO} if
     *         the radius is determined automatically
     * @see #setMaxRadius(int)
     * @hide
     */
    public int getMaxRadius() {
        return mState.mMaxRadius;
    }

    private RippleDrawable(RippleState state, Resources res, Theme theme) {
        boolean needsTheme = false;

        final RippleState ns;
        if (theme != null && state != null && state.canApplyTheme()) {
            ns = new RippleState(state, this, res);
            needsTheme = true;
        } else if (state == null) {
            ns = new RippleState(null, this, res);
        } else {
            // We always need a new state since child drawables contain local
            // state but live within the parent's constant state.
            // TODO: Move child drawables into local state.
            ns = new RippleState(state, this, res);
        }

        if (res != null) {
            mDensity = res.getDisplayMetrics().density;
        }

        mState = ns;
        mLayerState = ns;

        if (ns.mNum > 0) {
            ensurePadding();
        }

        if (needsTheme) {
            applyTheme(theme);
        }

        initializeFromState();
    }

    private void initializeFromState() {
        // Initialize from constant state.
        mMask = findDrawableByLayerId(R.id.mask);
    }
}
