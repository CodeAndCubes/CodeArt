package com.mrleonardos.codeart.client;

import com.mrleonardos.codecore.platform.client.image.ImageMipmaps;
import com.mrleonardos.codecore.platform.client.image.ProgressiveTexture;

/**
 * Текстура арта поверх ядра: заливка порциями и память общие, кадры остаются моду.
 *
 * <p>
 * Статичный арта строится из одного массива пикселей, и мип-цепочку ему считает ядро. Атласу кадров
 * мипмапы запрещены: уменьшение усреднило бы соседние кадры анимации, поэтому атлас подаётся ядру
 * одноуровневой цепочкой и рисуется жёсткими пикселями. Кадровая раскладка нужна только атласу и
 * отсутствует у статики: одно необязательное поле вместо двух форм одного класса.
 */
public final class ClientArtTexture implements ArtTexture {

    private final ProgressiveTexture texture;
    private final Frames frames;
    private final long memoryFootprint;
    private long lastUsedMillis;

    private ClientArtTexture(ProgressiveTexture texture, Frames frames, long memoryFootprint) {
        this.texture = texture;
        this.frames = frames;
        this.memoryFootprint = memoryFootprint;
    }

    public static ClientArtTexture still(int[] pixels, int width, int height, boolean smooth) {
        long bytes = 0L;
        int level = 0;
        while (true) {
            bytes += (long) ImageMipmaps.width(level, width) * ImageMipmaps.height(level, height) * 4L;
            if (ImageMipmaps.width(level, width) == 1 && ImageMipmaps.height(level, height) == 1) {
                break;
            }
            level++;
        }
        return new ClientArtTexture(new ProgressiveTexture(pixels, width, height, smooth), null, bytes);
    }

    public static ClientArtTexture atlas(int[] atlas, int atlasWidth, int atlasHeight, int frameWidth, int frameHeight,
        int columns, int frameCount, int[] frameDelaysMillis) {
        ProgressiveTexture texture = new ProgressiveTexture(new int[][] { atlas }, atlasWidth, atlasHeight, false);
        return new ClientArtTexture(
            texture,
            new Frames(atlasWidth, atlasHeight, frameWidth, frameHeight, columns, frameCount, frameDelaysMillis),
            (long) atlasWidth * atlasHeight * 4L);
    }

    @Override
    public void uploadStep() {
        texture.uploadStep();
    }

    @Override
    public boolean isReady() {
        return texture.isReady();
    }

    @Override
    public float uploadProgress() {
        return texture.uploadProgress();
    }

    @Override
    public void bind() {
        texture.bind();
    }

    @Override
    public void dispose() {
        texture.dispose();
    }

    @Override
    public boolean isAnimated() {
        return frames != null && frames.frameCount > 1;
    }

    @Override
    public int frameAt(long elapsedMillis) {
        return frames == null ? 0 : frames.frameAt(elapsedMillis);
    }

    @Override
    public float minU(int frame) {
        return frames == null ? 0.0F : frames.minU(frame);
    }

    @Override
    public float maxU(int frame) {
        return frames == null ? 1.0F : frames.maxU(frame);
    }

    @Override
    public float minV(int frame) {
        return frames == null ? 0.0F : frames.minV(frame);
    }

    @Override
    public float maxV(int frame) {
        return frames == null ? 1.0F : frames.maxV(frame);
    }

    @Override
    public long memoryFootprint() {
        return memoryFootprint;
    }

    @Override
    public long lastUsedMillis() {
        return lastUsedMillis;
    }

    @Override
    public void markUsed(long now) {
        lastUsedMillis = now;
    }

    /** Раскладка кадров атласа: сетка, задержки и координаты кадра в текстуре. */
    private static final class Frames {

        private final int atlasWidth;
        private final int atlasHeight;
        private final int frameWidth;
        private final int frameHeight;
        private final int columns;
        private final int frameCount;
        private final int[] frameDelaysMillis;
        private final int totalDurationMillis;

        Frames(int atlasWidth, int atlasHeight, int frameWidth, int frameHeight, int columns, int frameCount,
            int[] frameDelaysMillis) {
            this.atlasWidth = atlasWidth;
            this.atlasHeight = atlasHeight;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.columns = columns;
            this.frameCount = frameCount;
            this.frameDelaysMillis = frameDelaysMillis;
            int total = 0;
            for (int delay : frameDelaysMillis) {
                total += delay;
            }
            this.totalDurationMillis = Math.max(total, 1);
        }

        int frameAt(long elapsedMillis) {
            if (frameCount <= 1) {
                return 0;
            }
            long position = elapsedMillis % totalDurationMillis;
            for (int i = 0; i < frameCount; i++) {
                position -= frameDelaysMillis[i];
                if (position < 0) {
                    return i;
                }
            }
            return frameCount - 1;
        }

        float minU(int frame) {
            return (float) ((frame % columns) * frameWidth) / atlasWidth;
        }

        float maxU(int frame) {
            return (float) ((frame % columns + 1) * frameWidth) / atlasWidth;
        }

        float minV(int frame) {
            return (float) ((frame / columns) * frameHeight) / atlasHeight;
        }

        float maxV(int frame) {
            return (float) ((frame / columns + 1) * frameHeight) / atlasHeight;
        }
    }
}
