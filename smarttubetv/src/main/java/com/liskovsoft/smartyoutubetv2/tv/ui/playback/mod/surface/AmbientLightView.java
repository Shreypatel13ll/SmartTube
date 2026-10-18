package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.Player;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

/** Native, low-resolution video edge glow. The playback surface remains above this view. */
public final class AmbientLightView extends View {
    private static final int CAPTURE_WIDTH = 64;
    private static final int CAPTURE_HEIGHT = 36;
    private static final int CAPTURE_INTERVAL_MS = 100;
    private static final int GLOW_BANDS = 20;
    private static final float VIDEO_INSET = 0.07f;
    private static final float COLOR_BLEND = 0.45f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[][] mColors = new int[4][AmbientLightSampler.SAMPLES];
    private final int[][] mNextColors = new int[4][AmbientLightSampler.SAMPLES];
    private final PlayerTweaksData mSettings;
    private final Runnable mCapture = new Runnable() {
        @Override
        public void run() {
            if (!mActive) {
                return;
            }
            updateVideoMargins();
            if (mSettings.isAmbientLightEnabled() && mSource != null && mSource.isAvailable() &&
                    mPlayer != null && mPlayer.getPlayWhenReady() && mPlayer.getPlaybackState() == Player.STATE_READY) {
                captureFrame();
            }
            postDelayed(this, mSettings.isAmbientLightEnabled() ? CAPTURE_INTERVAL_MS : 1_000);
        }
    };
    private TextureView mSource;
    private View mVideoRoot;
    private Player mPlayer;
    private boolean mActive;
    private boolean mHasFrame;

    public AmbientLightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSettings = PlayerTweaksData.instance(context);
        setFocusable(false);
        setClickable(false);
    }

    public void setVideoSource(TextureView source, View videoRoot) {
        mSource = source;
        mVideoRoot = videoRoot;
        updateVideoMargins();
    }

    public void setPlayer(Player player) {
        mPlayer = player;
        if (player == null) {
            mHasFrame = false;
            invalidate();
        }
    }

    public void setActive(boolean active) {
        mActive = active;
        removeCallbacks(mCapture);
        if (active) {
            post(mCapture);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateVideoMargins();
    }

    @Override
    protected void onDetachedFromWindow() {
        setActive(false);
        super.onDetachedFromWindow();
    }

    private void updateVideoMargins() {
        if (mVideoRoot == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mVideoRoot.getLayoutParams();
        boolean enabled = mSource != null && mSettings.isAmbientLightEnabled();
        int horizontal = enabled ? Math.round(getWidth() * VIDEO_INSET) : 0;
        int vertical = enabled ? Math.round(getHeight() * VIDEO_INSET) : 0;
        if (params.leftMargin != horizontal || params.rightMargin != horizontal ||
                params.topMargin != vertical || params.bottomMargin != vertical) {
            params.setMargins(horizontal, vertical, horizontal, vertical);
            mVideoRoot.setLayoutParams(params);
            invalidate();
        }
    }

    private void captureFrame() {
        Bitmap frame;
        try {
            frame = mSource.getBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT);
        } catch (RuntimeException e) {
            // Protected or device-specific video surfaces may not allow frame reads.
            return;
        }
        if (frame == null) {
            return;
        }
        try {
            int[] pixels = new int[CAPTURE_WIDTH * CAPTURE_HEIGHT];
            frame.getPixels(pixels, 0, CAPTURE_WIDTH, 0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT);
            AmbientLightSampler.sample(pixels, CAPTURE_WIDTH, CAPTURE_HEIGHT, mNextColors);
            for (int side = 0; side < mColors.length; side++) {
                for (int sample = 0; sample < AmbientLightSampler.SAMPLES; sample++) {
                    mColors[side][sample] = mHasFrame ? blend(mColors[side][sample], mNextColors[side][sample]) :
                            mNextColors[side][sample];
                }
            }
            mHasFrame = true;
            invalidate();
        } finally {
            frame.recycle();
        }
    }

    private static int blend(int oldColor, int newColor) {
        int red = Math.round(((oldColor >> 16) & 0xff) * (1 - COLOR_BLEND) +
                ((newColor >> 16) & 0xff) * COLOR_BLEND);
        int green = Math.round(((oldColor >> 8) & 0xff) * (1 - COLOR_BLEND) +
                ((newColor >> 8) & 0xff) * COLOR_BLEND);
        int blue = Math.round((oldColor & 0xff) * (1 - COLOR_BLEND) + (newColor & 0xff) * COLOR_BLEND);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mHasFrame || mVideoRoot == null || !mSettings.isAmbientLightEnabled()) {
            return;
        }
        float left = mVideoRoot.getLeft();
        float top = mVideoRoot.getTop();
        float right = mVideoRoot.getRight();
        float bottom = mVideoRoot.getBottom();
        if (right <= left || bottom <= top) {
            return;
        }

        float spread = mSettings.getAmbientLightSpread() / 100f;
        float strength = mSettings.getAmbientLightStrength() / 100f;
        drawHorizontalGlow(canvas, mColors[AmbientLightSampler.TOP], left, right, top,
                top * spread, true, strength);
        drawHorizontalGlow(canvas, mColors[AmbientLightSampler.BOTTOM], left, right, bottom,
                (getHeight() - bottom) * spread, false, strength);
        drawVerticalGlow(canvas, mColors[AmbientLightSampler.LEFT], top, bottom, left,
                left * spread, true, strength);
        drawVerticalGlow(canvas, mColors[AmbientLightSampler.RIGHT], top, bottom, right,
                (getWidth() - right) * spread, false, strength);
        drawCorner(canvas, left, top, mColors[AmbientLightSampler.TOP][0], spread, strength);
        drawCorner(canvas, right, top, mColors[AmbientLightSampler.TOP][AmbientLightSampler.SAMPLES - 1], spread, strength);
        drawCorner(canvas, left, bottom, mColors[AmbientLightSampler.BOTTOM][0], spread, strength);
        drawCorner(canvas, right, bottom, mColors[AmbientLightSampler.BOTTOM][AmbientLightSampler.SAMPLES - 1], spread, strength);
        mPaint.setShader(null);
    }

    private void drawHorizontalGlow(Canvas canvas, int[] colors, float left, float right, float edge,
                                    float span, boolean above, float strength) {
        if (span <= 0) {
            return;
        }
        mPaint.setShader(new LinearGradient(left, 0, right, 0, colors, null, Shader.TileMode.CLAMP));
        for (int band = 0; band < GLOW_BANDS; band++) {
            float inner = span * band / GLOW_BANDS;
            float outer = span * (band + 1) / GLOW_BANDS;
            mPaint.setAlpha(bandAlpha(band, strength));
            canvas.drawRect(left, above ? edge - outer : edge + inner,
                    right, above ? edge - inner : edge + outer, mPaint);
        }
    }

    private void drawVerticalGlow(Canvas canvas, int[] colors, float top, float bottom, float edge,
                                  float span, boolean before, float strength) {
        if (span <= 0) {
            return;
        }
        mPaint.setShader(new LinearGradient(0, top, 0, bottom, colors, null, Shader.TileMode.CLAMP));
        for (int band = 0; band < GLOW_BANDS; band++) {
            float inner = span * band / GLOW_BANDS;
            float outer = span * (band + 1) / GLOW_BANDS;
            mPaint.setAlpha(bandAlpha(band, strength));
            canvas.drawRect(before ? edge - outer : edge + inner, top,
                    before ? edge - inner : edge + outer, bottom, mPaint);
        }
    }

    private void drawCorner(Canvas canvas, float x, float y, int color, float spread, float strength) {
        float radius = Math.min(getWidth(), getHeight()) * VIDEO_INSET * spread * 1.4f;
        if (radius <= 0) {
            return;
        }
        mPaint.setShader(new RadialGradient(x, y, radius,
                new int[] {color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP));
        mPaint.setAlpha(Math.min(255, Math.round(140 * strength)));
        canvas.drawCircle(x, y, radius, mPaint);
    }

    private static int bandAlpha(int band, float strength) {
        float distance = (band + 0.5f) / GLOW_BANDS;
        return Math.min(255, Math.round(175 * strength * (1 - distance) * (1 - distance)));
    }
}
