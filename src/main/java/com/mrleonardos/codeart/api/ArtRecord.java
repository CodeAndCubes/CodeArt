package com.mrleonardos.codeart.api;

public final class ArtRecord {

    private final String name;
    private final int widthBlocks;
    private final int heightBlocks;
    private final String source;
    private final String sha256;
    private final ArtFormat format;
    private final int byteSize;
    private final int pixelWidth;
    private final int pixelHeight;
    private final int frameCount;

    public ArtRecord(String name, int widthBlocks, int heightBlocks, String source, String sha256, ArtFormat format,
        int byteSize, int pixelWidth, int pixelHeight, int frameCount) {
        this.name = name;
        this.widthBlocks = widthBlocks;
        this.heightBlocks = heightBlocks;
        this.source = source;
        this.sha256 = sha256;
        this.format = format;
        this.byteSize = byteSize;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.frameCount = frameCount;
    }

    public static ArtRecord of(String name, int widthBlocks, int heightBlocks, String source) {
        return new ArtRecord(name, widthBlocks, heightBlocks, source, null, null, 0, 0, 0, 0);
    }

    public ArtRecord withMetadata(ArtDefinition definition) {
        return new ArtRecord(
            name,
            widthBlocks,
            heightBlocks,
            source,
            definition.sha256(),
            definition.format(),
            definition.byteSize(),
            definition.pixelWidth(),
            definition.pixelHeight(),
            definition.frameCount());
    }

    public boolean hasMetadata() {
        return sha256 != null && format != null && byteSize > 0 && pixelWidth > 0 && pixelHeight > 0 && frameCount > 0;
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

    public String source() {
        return source;
    }

    public String sha256() {
        return sha256;
    }

    public ArtFormat format() {
        return format;
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
}
