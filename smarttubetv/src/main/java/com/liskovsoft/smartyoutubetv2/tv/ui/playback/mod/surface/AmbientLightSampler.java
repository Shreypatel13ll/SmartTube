package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import com.liskovsoft.smartyoutubetv2.common.prefs.AmbientLightDefaults;

/** Extracts a small set of colors from the visible edges of a downscaled video frame. */
final class AmbientLightSampler {
    static final int SAMPLES = 12;
    static final int TOP = 0;
    static final int RIGHT = 1;
    static final int BOTTOM = 2;
    static final int LEFT = 3;
    private static final int NON_BLACK_SUM = 54;

    private AmbientLightSampler() {
    }

    static void sample(int[] pixels, int width, int height, int[][] edges) {
        if (width < 4 || height < 4 || pixels.length < width * height) {
            throw new IllegalArgumentException("Invalid video frame");
        }

        int top = 0;
        int bottom = height - 1;
        int left = 0;
        int right = width - 1;

        // The reference profile detects horizontal bars, but leaves vertical bar detection off.
        if (AmbientLightDefaults.DETECT_HORIZONTAL_BARS) {
            while (top < height / 4 && isBlackRow(pixels, width, top, left, right)) {
                top++;
            }
            while (bottom > height * 3 / 4 && isBlackRow(pixels, width, bottom, left, right)) {
                bottom--;
            }
        }

        int horizontalBand = Math.max(1, Math.min(3, (bottom - top + 1) / 12));
        int verticalBand = Math.max(1, Math.min(3, (right - left + 1) / 12));
        for (int i = 0; i < SAMPLES; i++) {
            int x0 = left + (right - left + 1) * i / SAMPLES;
            int x1 = Math.max(x0, left + (right - left + 1) * (i + 1) / SAMPLES - 1);
            int y0 = top + (bottom - top + 1) * i / SAMPLES;
            int y1 = Math.max(y0, top + (bottom - top + 1) * (i + 1) / SAMPLES - 1);
            edges[TOP][i] = average(pixels, width, x0, top, x1, top + horizontalBand - 1);
            edges[BOTTOM][i] = average(pixels, width, x0, bottom - horizontalBand + 1, x1, bottom);
            edges[LEFT][i] = average(pixels, width, left, y0, left + verticalBand - 1, y1);
            edges[RIGHT][i] = average(pixels, width, right - verticalBand + 1, y0, right, y1);
        }
    }

    static boolean hasVisibleColor(int[][] edges) {
        for (int[] side : edges) {
            for (int color : side) {
                if (isNonBlack(color)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Blur across each sampled edge while preserving its overall hue. */
    static void blur(int[][] source, int[][] target, int blurPercentage) {
        int radius = Math.max(0, Math.min(SAMPLES - 1, Math.round(blurPercentage / 12.5f)));
        for (int side = 0; side < source.length; side++) {
            for (int sample = 0; sample < SAMPLES; sample++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int count = 0;
                for (int neighbor = Math.max(0, sample - radius);
                        neighbor <= Math.min(SAMPLES - 1, sample + radius); neighbor++) {
                    int color = source[side][neighbor];
                    red += (color >> 16) & 0xff;
                    green += (color >> 8) & 0xff;
                    blue += color & 0xff;
                    count++;
                }
                target[side][sample] = 0xff000000 | (red / count) << 16 |
                        (green / count) << 8 | blue / count;
            }
        }
    }

    private static boolean isBlackRow(int[] pixels, int width, int y, int left, int right) {
        int bright = 0;
        for (int x = left; x <= right; x++) {
            if (isNonBlack(pixels[y * width + x])) {
                bright++;
            }
        }
        return bright < Math.max(1, (right - left + 1) / 6);
    }

    private static boolean isNonBlack(int color) {
        return ((color >> 16) & 0xff) + ((color >> 8) & 0xff) + (color & 0xff) > NON_BLACK_SUM;
    }

    private static int average(int[] pixels, int width, int left, int top, int right, int bottom) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                int color = pixels[y * width + x];
                red += (color >> 16) & 0xff;
                green += (color >> 8) & 0xff;
                blue += color & 0xff;
                count++;
            }
        }
        return 0xff000000 | (red / count) << 16 | (green / count) << 8 | blue / count;
    }
}
