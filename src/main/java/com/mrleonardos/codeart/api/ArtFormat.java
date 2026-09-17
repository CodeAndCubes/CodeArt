package com.mrleonardos.codeart.api;

public enum ArtFormat {

    PNG("png"),
    JPEG("jpg"),
    GIF("gif");

    private static final ArtFormat[] VALUES = values();

    private final String extension;

    ArtFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

}
