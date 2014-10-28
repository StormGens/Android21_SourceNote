/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff.Mode;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.Resources.Theme;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Log;
import android.os.SystemClock;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import com.android.internal.R;

/**
 * @hide
 */
public class AnimatedRotateDrawable extends Drawable implements Drawable.Callback, Runnable,
        Animatable {

    private AnimatedRotateState mState;
    private boolean mMutated;
    private float mCurrentDegrees;
    private float mIncrement;
    private boolean mRunning;

    public AnimatedRotateDrawable() {
        this(null, null);
    }

    private AnimatedRotateDrawable(AnimatedRotateState rotateState, Resources res) {
        mState = new AnimatedRotateState(rotateState, this, res);
        init();
    }

    private void init() {
        final AnimatedRotateState state = mState;
        mIncrement = 360.0f / state.mFramesCount;
        final Drawable drawable = state.mDrawable;
        if (drawable != null) {
            drawable.setFilterBitmap(true);
            if (drawable instanceof BitmapDrawable) {
                ((BitmapDrawable) drawable).setAntiAlias(true);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        int saveCount = canvas.save();

        final AnimatedRotateState st = mState;
        final Drawable drawable = st.mDrawable;
        final Rect bounds = drawable.getBounds();

        int w = bounds.right - bounds.left;
        int h = bounds.bottom - bounds.top;

        float px = st.mPivotXRel ? (w * st.mPivotX) : st.mPivotX;
        float py = st.mPivotYRel ? (h * st.mPivotY) : st.mPivotY;

        canvas.rotate(mCurrentDegrees, px + bounds.left, py + bounds.top);

        drawable.draw(canvas);

        canvas.restoreToCount(saveCount);
    }

    @Override
    public void start() {
        if (!mRunning) {
            mRunning = true;
            nextFrame();
        }
    }

    @Override
    public void stop() {
        mRunning = false;
        unscheduleSelf(this);
    }

    @Override
    public boolean isRunning() {
        return mRunning;
    }

    private void nextFrame() {
        unscheduleSelf(this);
        scheduleSelf(this, SystemClock.uptimeMillis() + mState.mFrameDuration);
    }

    @Override
    public void run() {
        // TODO: This should be computed in draw(Canvas), based on the amount
        // of time since the last frame drawn
        mCurrentDegrees += mIncrement;
        if (mCurrentDegrees > (360.0f - mIncrement)) {
            mCurrentDegrees = 0.0f;
        }
        invalidateSelf();
        nextFrame();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mState.mDrawable.setVisible(visible, restart);
        boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (changed || restart) {
                mCurrentDegrees = 0.0f;
                nextFrame();
            }
        } else {
            unscheduleSelf(this);
        }
        return changed;
    }

    /**
     * Returns the drawable rotated by this RotateDrawable.
     */
    public Drawable getDrawable() {
        return mState.mDrawable;
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations()
                | mState.mChangingConfigurations
                | mState.mDrawable.getChangingConfigurations();
    }

    @Override
    public void setAlpha(int alpha) {
        mState.mDrawable.setAlpha(alpha);
    }

    @Override
    public int getAlpha() {
        return mState.mDrawable.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mState.mDrawable.setColorFilter(cf);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        mState.mDrawable.setTintList(tint);
    }

    @Override
    public void setTintMode(Mode tintMode) {
        mState.mDrawable.setTintMode(tintMode);
    }

    @Override
    public int getOpacity() {
        return mState.mDrawable.getOpacity();
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.invalidateDrawable(this);
        }
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.scheduleDrawable(this, what, when);
        }
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.unscheduleDrawable(this, what);
        }
    }

    @Override
    public boolean getPadding(Rect padding) {
        return mState.mDrawable.getPadding(padding);
    }

    @Override
    public boolean isStateful() {
        return mState.mDrawable.isStateful();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mState.mDrawable.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    @Override
    protected boolean onLevelChange(int level) {
        return mState.mDrawable.setLevel(level);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return mState.mDrawable.setState(state);
    }

    @Override
    public int getIntrinsicWidth() {
        return mState.mDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mState.mDrawable.getIntrinsicHeight();
    }

    @Override
    public ConstantState getConstantState() {
        if (mState.canConstantState()) {
            mState.mChangingConfigurations = getChangingConfigurations();
            return mState;
        }
        return null;
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
            throws XmlPullParserException, IOException {

        final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.AnimatedRotateDrawable);

        super.inflateWithAttributes(r, parser, a, R.styleable.AnimatedRotateDrawable_visible);

        TypedValue tv = a.peekValue(R.styleable.AnimatedRotateDrawable_pivotX);
        final boolean pivotXRel = tv.type == TypedValue.TYPE_FRACTION;
        final float pivotX = pivotXRel ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();

        tv = a.peekValue(R.styleable.AnimatedRotateDrawable_pivotY);
        final boolean pivotYRel = tv.type == TypedValue.TYPE_FRACTION;
        final float pivotY = pivotYRel ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();

        setFramesCount(a.getInt(R.styleable.AnimatedRotateDrawable_framesCount, 12));
        setFramesDuration(a.getInt(R.styleable.AnimatedRotateDrawable_frameDuration, 150));

        final int res = a.getResourceId(R.styleable.AnimatedRotateDrawable_drawable, 0);
        Drawable drawable = null;
        if (res > 0) {
            drawable = r.getDrawable(res, theme);
        }

        a.recycle();

        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT &&
               (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {

            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            if ((drawable = Drawable.createFromXmlInner(r, parser, attrs, theme)) == null) {
                Log.w("drawable", "Bad element under <animated-rotate>: "
                        + parser .getName());
            }
        }

        if (drawable == null) {
            Log.w("drawable", "No drawable specified for <animated-rotate>");
        }

        final AnimatedRotateState rotateState = mState;
        rotateState.mDrawable = drawable;
        rotateState.mPivotXRel = pivotXRel;
        rotateState.mPivotX = pivotX;
        rotateState.mPivotYRel = pivotYRel;
        rotateState.mPivotY = pivotY;

        init();

        if (drawable != null) {
            drawable.setCallback(this);
        }
    }

    public void setFramesCount(int framesCount) {
        mState.mFramesCount = framesCount;
        mIncrement = 360.0f / mState.mFramesCount;
    }

    public void setFramesDuration(int framesDuration) {
        mState.mFrameDuration = framesDuration;
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mState.mDrawable.mutate();
            mMutated = true;
        }
        return this;
    }

    final static class AnimatedRotateState extends Drawable.ConstantState {
        Drawable mDrawable;

        int mChangingConfigurations;

        boolean mPivotXRel;
        float mPivotX;
        boolean mPivotYRel;
        float mPivotY;
        int mFrameDuration;
        int mFramesCount;

        private boolean mCanConstantState;
        private boolean mCheckedConstantState;

        public AnimatedRotateState(AnimatedRotateState orig, AnimatedRotateDrawable owner,
                Resources res) {
            if (orig != null) {
                if (res != null) {
                    mDrawable = orig.mDrawable.getConstantState().newDrawable(res);
                } else {
                    mDrawable = orig.mDrawable.getConstantState().newDrawable();
                }
                mDrawable.setCallback(owner);
                mDrawable.setLayoutDirection(orig.mDrawable.getLayoutDirection());
                mDrawable.setBounds(orig.mDrawable.getBounds());
                mDrawable.setLevel(orig.mDrawable.getLevel());
                mPivotXRel = orig.mPivotXRel;
                mPivotX = orig.mPivotX;
                mPivotYRel = orig.mPivotYRel;
                mPivotY = orig.mPivotY;
                mFramesCount = orig.mFramesCount;
                mFrameDuration = orig.mFrameDuration;
                mCanConstantState = mCheckedConstantState = true;
            }
        }

        @Override
        public Drawable newDrawable() {
            return new AnimatedRotateDrawable(this, null);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new AnimatedRotateDrawable(this, res);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }

        public boolean canConstantState() {
            if (!mCheckedConstantState) {
                mCanConstantState = mDrawable.getConstantState() != null;
                mCheckedConstantState = true;
            }

            return mCanConstantState;
        }
    }
}
