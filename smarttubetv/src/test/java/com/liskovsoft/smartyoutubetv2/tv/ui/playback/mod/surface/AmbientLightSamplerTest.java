package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class AmbientLightSamplerTest {
    @Test
    public void samplesPictureEdgesInsideLetterboxAndPillarboxBars() {
        int width = 24;
        int height = 20;
        int[] frame = new int[width * height];
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                int color = 0xff333333;
                if (y == 2) color = 0xffff0000;
                if (y == height - 3) color = 0xff00ff00;
                if (x <= 3) color = 0xff0000ff;
                if (x >= width - 4) color = 0xffffff00;
                frame[y * width + x] = color;
            }
        }

        int[][] edges = new int[4][AmbientLightSampler.SAMPLES];
        AmbientLightSampler.sample(frame, width, height, edges);

        assertEquals(0xffff0000, edges[AmbientLightSampler.TOP][6]);
        assertEquals(0xff00ff00, edges[AmbientLightSampler.BOTTOM][6]);
        assertEquals(0xff0000ff, edges[AmbientLightSampler.LEFT][6]);
        assertEquals(0xffffff00, edges[AmbientLightSampler.RIGHT][6]);
    }

    @Test
    public void darkFrameDoesNotProduceAFalseGlow() {
        int[] frame = new int[64 * 36];
        Arrays.fill(frame, 0xff000000);
        int[][] edges = new int[4][AmbientLightSampler.SAMPLES];

        AmbientLightSampler.sample(frame, 64, 36, edges);

        for (int[] side : edges) {
            for (int color : side) {
                assertEquals(0xff000000, color);
            }
        }
    }
}
