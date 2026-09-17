package com.mrleonardos.codeart.api;

import java.util.regex.Pattern;

import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.Codec;
import com.mrleonardos.codecore.api.net.MalformedPacketException;

/**
 * Арт, каким его видят обе стороны: имя, размер полотна и полного описания картинки.
 *
 * <p>
 * Единственное место сетевой сериализации арта: строки через {@link Codec}, хеш сырыми байтами. Манифест,
 * обновления и запросы картинки ездят одними и теми же полями, и расхождение форматов им негде завестись.
 *
 * <p>
 * Конструктор проверяет каждое поле: определение приезжает по сети, и верить ему нельзя. Проверка на
 * границе держит инвариант «собранный объект корректен» без отдельного метода валидации, про который
 * вызывающий забыл бы.
 */
public final class ArtDefinition {

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_BLOCKS_PER_SIDE = 64;
    public static final int HARD_MAX_BYTES = 64 * 1024 * 1024;
    public static final int HARD_MAX_PIXELS_PER_SIDE = 32768;
    public static final int HARD_MAX_FRAMES = 4096;
    public static final int MAX_URL_LENGTH = 1024;

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    private final String name;
    private final int widthBlocks;
    private final int heightBlocks;
    private final ArtFormat format;
    private final String sha256;
    private final int byteSize;
    private final int pixelWidth;
    private final int pixelHeight;
    private final int frameCount;
    private final String directUrl;

    public ArtDefinition(String name, int widthBlocks, int heightBlocks, ArtFormat format, String sha256, int byteSize,
        int pixelWidth, int pixelHeight, int frameCount, String directUrl) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid art name: " + name);
        }
        if (widthBlocks < 1 || widthBlocks > MAX_BLOCKS_PER_SIDE
            || heightBlocks < 1
            || heightBlocks > MAX_BLOCKS_PER_SIDE) {
            throw new IllegalArgumentException("Invalid canvas size: " + widthBlocks + "x" + heightBlocks);
        }
        if (format == null) {
            throw new IllegalArgumentException("Missing art format");
        }
        if (!Hashes.isSha256Hex(sha256)) {
            throw new IllegalArgumentException("Invalid sha256: " + sha256);
        }
        if (byteSize < 1 || byteSize > HARD_MAX_BYTES) {
            throw new IllegalArgumentException("Invalid byte size: " + byteSize);
        }
        if (pixelWidth < 1 || pixelWidth > HARD_MAX_PIXELS_PER_SIDE
            || pixelHeight < 1
            || pixelHeight > HARD_MAX_PIXELS_PER_SIDE) {
            throw new IllegalArgumentException("Invalid image size: " + pixelWidth + "x" + pixelHeight);
        }
        if (frameCount < 1 || frameCount > HARD_MAX_FRAMES) {
            throw new IllegalArgumentException("Invalid frame count: " + frameCount);
        }
        if (directUrl != null && directUrl.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Direct url is too long");
        }
        this.name = name;
        this.widthBlocks = widthBlocks;
        this.heightBlocks = heightBlocks;
        this.format = format;
        this.sha256 = sha256;
        this.byteSize = byteSize;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.frameCount = frameCount;
        this.directUrl = directUrl == null || directUrl.isEmpty() ? null : directUrl;
    }

    public String name() {
        return name;
    }

    public int widthBlocks() {
        return widthBlocks;
    }

    public int heightBlocks() {
        return heightBlocks;
    }

    public ArtFormat format() {
        return format;
    }

    public String sha256() {
        return sha256;
    }

    public int byteSize() {
        return byteSize;
    }

    public int pixelWidth() {
        return pixelWidth;
    }

    public int pixelHeight() {
        return pixelHeight;
    }

    public int frameCount() {
        return frameCount;
    }

    public boolean isAnimated() {
        return frameCount > 1;
    }

    public String directUrl() {
        return directUrl;
    }

    public ArtDefinition withDirectUrl(String url) {
        return new ArtDefinition(
            name,
            widthBlocks,
            heightBlocks,
            format,
            sha256,
            byteSize,
            pixelWidth,
            pixelHeight,
            frameCount,
            url);
    }

    public long estimatedPixelCount() {
        return (long) pixelWidth * pixelHeight * frameCount;
    }

    public void write(CodeBuffer buffer) {
        Codec.writeString(buffer, name);
        buffer.writeInt(widthBlocks);
        buffer.writeInt(heightBlocks);
        Codec.writeEnum(buffer, format);
        buffer.writeBytes(Hashes.fromHex(sha256));
        buffer.writeInt(byteSize);
        buffer.writeInt(pixelWidth);
        buffer.writeInt(pixelHeight);
        buffer.writeInt(frameCount);
        Codec.writeOptionalString(buffer, directUrl);
    }

    public static ArtDefinition read(CodeBuffer buffer) {
        String name = Codec.readString(buffer);
        int widthBlocks = buffer.readInt();
        int heightBlocks = buffer.readInt();
        ArtFormat format = Codec.readEnum(buffer, ArtFormat.class);
        byte[] hash = new byte[Hashes.SHA256_BYTES];
        buffer.readBytes(hash);
        int byteSize = buffer.readInt();
        int pixelWidth = buffer.readInt();
        int pixelHeight = buffer.readInt();
        int frameCount = buffer.readInt();
        String directUrl = Codec.readOptionalString(buffer);
        try {
            return new ArtDefinition(
                name,
                widthBlocks,
                heightBlocks,
                format,
                Hashes.toHex(hash),
                byteSize,
                pixelWidth,
                pixelHeight,
                frameCount,
                directUrl);
        } catch (IllegalArgumentException rejected) {
            throw new MalformedPacketException("Art definition failed validation: " + rejected.getMessage());
        }
    }

    public static boolean isValidName(String name) {
        return name != null && name.length() <= MAX_NAME_LENGTH
            && NAME_PATTERN.matcher(name)
                .matches();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ArtDefinition)) {
            return false;
        }
        ArtDefinition that = (ArtDefinition) other;
        return widthBlocks == that.widthBlocks && heightBlocks == that.heightBlocks
            && byteSize == that.byteSize
            && pixelWidth == that.pixelWidth
            && pixelHeight == that.pixelHeight
            && frameCount == that.frameCount
            && format == that.format
            && name.equals(that.name)
            && sha256.equals(that.sha256)
            && (directUrl == null ? that.directUrl == null : directUrl.equals(that.directUrl));
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + sha256.hashCode();
        result = 31 * result + widthBlocks;
        result = 31 * result + heightBlocks;
        return result;
    }

    @Override
    public String toString() {
        return "ArtDefinition[" + name
            + " "
            + widthBlocks
            + "x"
            + heightBlocks
            + " "
            + format
            + " "
            + pixelWidth
            + "x"
            + pixelHeight
            + (isAnimated() ? " frames=" + frameCount : "")
            + " sha="
            + sha256.substring(0, 8)
            + "]";
    }
}
