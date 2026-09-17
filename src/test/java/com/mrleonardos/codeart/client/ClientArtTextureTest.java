package com.mrleonardos.codeart.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Кадровая математика атласа без OpenGL: конструктор текстуры ядра только считает, заливка начинается
 * с первым uploadStep, которого здесь нет.
 */
class ClientArtTextureTest {

    @Test
    void stillArtCoversTheWholeTexture() {
        ArtTexture texture = ClientArtTexture.still(new int[16 * 16], 16, 16, true);

        assertFalse(texture.isAnimated());
        assertEquals(0, texture.frameAt(10_000L));
        assertEquals(0.0F, texture.minU(0));
        assertEquals(1.0F, texture.maxU(0));
        assertEquals(0.0F, texture.minV(0));
        assertEquals(1.0F, texture.maxV(0));
        assertEquals((256 + 64 + 16 + 4 + 1) * 4L, texture.memoryFootprint(), "вся мип-цепочка: 16,8,4,2,1");
    }

    @Test
    void atlasFramesFollowTheirDelays() {
        int[] delays = { 100, 200, 300, 400 };
        ArtTexture texture = ClientArtTexture.atlas(new int[64 * 64], 64, 64, 32, 32, 2, 4, delays);

        assertTrue(texture.isAnimated());
        assertEquals(0, texture.frameAt(50L));
        assertEquals(1, texture.frameAt(150L));
        assertEquals(2, texture.frameAt(350L));
        assertEquals(3, texture.frameAt(750L));
        assertEquals(0, texture.frameAt(1000L), "цикл начинается заново");
    }

    @Test
    void atlasFramesGetTheirOwnCorners() {
        ArtTexture texture = ClientArtTexture
            .atlas(new int[64 * 64], 64, 64, 32, 32, 2, 4, new int[] { 100, 100, 100, 100 });

        assertEquals(0.0F, texture.minU(0));
        assertEquals(0.5F, texture.maxU(0));
        assertEquals(0.0F, texture.minV(0));
        assertEquals(0.5F, texture.maxV(0));

        assertEquals(0.5F, texture.minU(1));
        assertEquals(1.0F, texture.maxU(1));

        assertEquals(0.5F, texture.minV(2), "третий кадр это второй ряд сетки");
        assertEquals(1.0F, texture.maxV(2));
    }
}
