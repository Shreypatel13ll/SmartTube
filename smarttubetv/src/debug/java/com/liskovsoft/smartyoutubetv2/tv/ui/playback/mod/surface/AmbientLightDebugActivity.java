package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.Player;

import java.lang.reflect.Proxy;

/** Debug-only synthetic playback surface: exercises the real AmbientLightView without network video. */
public final class AmbientLightDebugActivity extends Activity {
    private final Paint mPaint = new Paint();
    private TextureView mTexture;
    private AmbientLightView mAmbient;
    private int mFrame;
    private final Runnable mDraw = new Runnable() {
        @Override
        public void run() {
            if (!mTexture.isAvailable()) {
                return;
            }
            Canvas canvas = mTexture.lockCanvas();
            if (canvas != null) {
                try {
                    int width = canvas.getWidth();
                    int height = canvas.getHeight();
                    canvas.drawColor(Color.rgb(23, 28, 35));
                    fill(canvas, Color.rgb(235, 60, 45), 0, 0, width, height / 5);
                    fill(canvas, Color.rgb(30, 160, 240), 0, height * 4 / 5, width, height);
                    fill(canvas, Color.rgb(190, 65, 200), 0, 0, width / 8, height);
                    fill(canvas, Color.rgb(40, 210, 100), width * 7 / 8, 0, width, height);
                    int x = width / 3 + (mFrame++ % 30) * width / 90;
                    fill(canvas, Color.WHITE, x, height / 3, x + width / 12, height * 2 / 3);
                } finally {
                    mTexture.unlockCanvasAndPost(canvas);
                }
            }
            mTexture.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        mAmbient = new AmbientLightView(this, null);
        root.addView(mAmbient, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout videoRoot = new FrameLayout(this);
        root.addView(videoRoot, new FrameLayout.LayoutParams(-1, -1));
        mTexture = new TextureView(this);
        videoRoot.addView(mTexture, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        mAmbient.setVideoSource(mTexture, videoRoot);
        Player playing = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    if ("getPlayWhenReady".equals(method.getName())) return true;
                    if ("getPlaybackState".equals(method.getName())) return Player.STATE_READY;
                    return null;
                });
        mAmbient.setPlayer(playing);
        mAmbient.setActive(true);
        mTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mTexture.post(mDraw);
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mTexture.removeCallbacks(mDraw);
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
        });
    }

    private void fill(Canvas canvas, int color, int left, int top, int right, int bottom) {
        mPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, mPaint);
    }

    @Override
    protected void onDestroy() {
        mTexture.removeCallbacks(mDraw);
        mAmbient.setActive(false);
        mAmbient.setPlayer(null);
        super.onDestroy();
    }
}
