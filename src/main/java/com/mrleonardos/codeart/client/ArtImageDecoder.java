package com.mrleonardos.codeart.client;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtFormat;
import com.mrleonardos.codecore.platform.client.image.ImageMipmaps;

/**
 * Разбор байтов арта в текстуру: статика отдельно, анимация атласом.
 *
 * <p>
 * Заливка в видеокарту и мипмапы живут в ядре: статике ядро считает цепочку уровней, атласу кадров
 * она запрещена. GIF композитится
 * здесь, потому что ImageIO отдаёт кадры как есть, без учёта disposal, а кадры укладываются в один атлас,
 * который анимируется сдвигом координат текстуры. Уменьшение кадров, когда атлас не лезет в лимит
 * видеокарты, берётся у ядра: усреднение с весом по альфе, иначе полупрозрачный край кадра темнел бы
 * ореолом.
 *
 * <p>
 * Потокобезопасности не требуется: декодер зовут только рабочие потоки клиента, и ни один вызов не
 * трогает OpenGL.
 */
public final class ArtImageDecoder {

    public static final int MAX_ATLAS_PIXELS = 4096 * 4096;

    private static final String GIF_METADATA_FORMAT = "javax_imageio_gif_image_1.0";
    private static final int DEFAULT_FRAME_DELAY_MILLIS = 100;
    private static final int MIN_FRAME_DELAY_MILLIS = 20;

    private ArtImageDecoder() {}

    public static ArtTexture decode(byte[] data, ArtDefinition definition, int maxTextureSize, int maxFrames,
        boolean animate, boolean smooth) throws IOException {
        int frameLimit = animate ? Math.max(1, maxFrames) : 1;
        return definition.format() == ArtFormat.GIF ? decodeGif(data, definition, frameLimit, maxTextureSize)
            : decodeStill(data, maxTextureSize, smooth);
    }

    private static ArtTexture decodeStill(byte[] data, int maxTextureSize, boolean smooth) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("No image reader accepted the data");
        }
        BufferedImage converted = toArgb(image);
        int width = converted.getWidth();
        int height = converted.getHeight();
        Layout layout = Layout.compute(width, height, 1, maxTextureSize);
        int[] pixels = pixelsOf(converted);
        if (layout.frameWidth != width || layout.frameHeight != height) {
            pixels = ImageMipmaps.downscale(pixels, width, height, layout.frameWidth, layout.frameHeight);
        }
        return ClientArtTexture.still(pixels, layout.frameWidth, layout.frameHeight, smooth);
    }

    private static ArtTexture decodeGif(byte[] data, ArtDefinition definition, int frameLimit, int maxTextureSize)
        throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF reader is available");
        }
        ImageReader reader = readers.next();
        List<int[]> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        Layout layout;
        int canvasWidth = definition.pixelWidth();
        int canvasHeight = definition.pixelHeight();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, false, false);
            int frameCount = Math.min(reader.getNumImages(true), frameLimit);
            if (frameCount < 1) {
                throw new IOException("GIF contains no frames");
            }
            layout = Layout.compute(canvasWidth, canvasHeight, frameCount, maxTextureSize);

            BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
            int[] canvasPixels = pixelsOf(canvas);
            Graphics2D graphics = canvas.createGraphics();
            int[] previous = null;
            try {
                for (int index = 0; index < frameCount; index++) {
                    BufferedImage raw = reader.read(index);
                    GifFrameMetadata metadata = readGifMetadata(reader.getImageMetadata(index));
                    if (metadata.disposal == Disposal.RESTORE_TO_PREVIOUS) {
                        previous = canvasPixels.clone();
                    }
                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(raw, metadata.left, metadata.top, null);

                    frames.add(
                        layout.frameWidth == canvasWidth && layout.frameHeight == canvasHeight ? canvasPixels.clone()
                            : ImageMipmaps.downscale(
                                canvasPixels,
                                canvasWidth,
                                canvasHeight,
                                layout.frameWidth,
                                layout.frameHeight));
                    delays.add(metadata.delayMillis);

                    if (metadata.disposal == Disposal.RESTORE_TO_BACKGROUND) {
                        graphics.setComposite(AlphaComposite.Clear);
                        graphics.fillRect(metadata.left, metadata.top, raw.getWidth(), raw.getHeight());
                    } else if (metadata.disposal == Disposal.RESTORE_TO_PREVIOUS && previous != null) {
                        System.arraycopy(previous, 0, canvasPixels, 0, canvasPixels.length);
                    }
                }
            } finally {
                graphics.dispose();
            }
        } finally {
            reader.dispose();
        }
        return assemble(frames, delays, layout);
    }

    private static ArtTexture assemble(List<int[]> frames, List<Integer> delays, Layout layout) {
        int frameCount = frames.size();
        int atlasWidth = layout.columns * layout.frameWidth;
        int atlasHeight = layout.rows * layout.frameHeight;
        int[] atlas = new int[atlasWidth * atlasHeight];
        int[] frameDelays = new int[frameCount];
        for (int index = 0; index < frameCount; index++) {
            int column = index % layout.columns;
            int row = index / layout.columns;
            int[] source = frames.get(index);
            for (int y = 0; y < layout.frameHeight; y++) {
                int target = (row * layout.frameHeight + y) * atlasWidth + column * layout.frameWidth;
                System.arraycopy(source, y * layout.frameWidth, atlas, target, layout.frameWidth);
            }
            frameDelays[index] = delays.get(index);
        }
        return ClientArtTexture.atlas(
            atlas,
            atlasWidth,
            atlasHeight,
            layout.frameWidth,
            layout.frameHeight,
            layout.columns,
            frameCount,
            frameDelays);
    }

    private static BufferedImage toArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static int[] pixelsOf(BufferedImage image) {
        return ((DataBufferInt) image.getRaster()
            .getDataBuffer()).getData();
    }

    private static GifFrameMetadata readGifMetadata(IIOMetadata metadata) {
        GifFrameMetadata result = new GifFrameMetadata();
        if (metadata == null) {
            return result;
        }
        Node root;
        try {
            root = metadata.getAsTree(GIF_METADATA_FORMAT);
        } catch (IllegalArgumentException e) {
            return result;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof IIOMetadataNode)) {
                continue;
            }
            IIOMetadataNode element = (IIOMetadataNode) node;
            if ("ImageDescriptor".equals(element.getNodeName())) {
                result.left = parseInt(element.getAttribute("imageLeftPosition"), 0);
                result.top = parseInt(element.getAttribute("imageTopPosition"), 0);
            } else if ("GraphicControlExtension".equals(element.getNodeName())) {
                int hundredths = parseInt(element.getAttribute("delayTime"), 0);
                result.delayMillis = hundredths <= 1 ? DEFAULT_FRAME_DELAY_MILLIS
                    : Math.max(MIN_FRAME_DELAY_MILLIS, hundredths * 10);
                result.disposal = Disposal.parse(element.getAttribute("disposalMethod"));
            }
        }
        return result;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class Layout {

        private final int frameWidth;
        private final int frameHeight;
        private final int columns;
        private final int rows;

        private Layout(int frameWidth, int frameHeight, int columns, int rows) {
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.columns = columns;
            this.rows = rows;
        }

        static Layout compute(int width, int height, int frameCount, int maxTextureSize) {
            int columns = (int) Math.ceil(Math.sqrt(frameCount));
            int rows = (frameCount + columns - 1) / columns;
            int limit = Math.max(64, maxTextureSize);
            int frameWidth = Math.max(1, width);
            int frameHeight = Math.max(1, height);
            while ((frameWidth > 1 || frameHeight > 1) && (columns * frameWidth > limit || rows * frameHeight > limit
                || (long) columns * frameWidth * rows * frameHeight > MAX_ATLAS_PIXELS)) {
                frameWidth = Math.max(1, frameWidth / 2);
                frameHeight = Math.max(1, frameHeight / 2);
            }
            return new Layout(frameWidth, frameHeight, columns, rows);
        }
    }

    private enum Disposal {

        NONE,
        RESTORE_TO_BACKGROUND,
        RESTORE_TO_PREVIOUS;

        static Disposal parse(String value) {
            if ("restoreToBackgroundColor".equals(value)) {
                return RESTORE_TO_BACKGROUND;
            }
            if ("restoreToPrevious".equals(value)) {
                return RESTORE_TO_PREVIOUS;
            }
            return NONE;
        }
    }

    private static final class GifFrameMetadata {

        private int left;
        private int top;
        private int delayMillis = DEFAULT_FRAME_DELAY_MILLIS;
        private Disposal disposal = Disposal.NONE;
    }
}
