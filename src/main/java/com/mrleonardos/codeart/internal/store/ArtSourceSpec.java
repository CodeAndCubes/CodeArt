package com.mrleonardos.codeart.internal.store;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

public final class ArtSourceSpec {

    public static final String LOCAL_PREFIX = "local://";

    public enum Kind {
        LOCAL,
        REMOTE
    }

    private final Kind kind;
    private final String raw;
    private final String localPath;
    private final URL url;

    private ArtSourceSpec(Kind kind, String raw, String localPath, URL url) {
        this.kind = kind;
        this.raw = raw;
        this.localPath = localPath;
        this.url = url;
    }

    public Kind kind() {
        return kind;
    }

    public String raw() {
        return raw;
    }

    public String localPath() {
        return localPath;
    }

    public URL url() {
        return url;
    }

    public boolean isRemote() {
        return kind == Kind.REMOTE;
    }

    public static ArtSourceSpec parse(String value) throws ArtSourceException {
        if (value == null) {
            throw new ArtSourceException("Empty art source");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ArtSourceException("Empty art source");
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            URL url;
            try {
                url = new URL(trimmed);
            } catch (MalformedURLException e) {
                throw new ArtSourceException("Malformed url: " + trimmed);
            }
            if (url.getHost() == null || url.getHost()
                .isEmpty()) {
                throw new ArtSourceException("Url has no host: " + trimmed);
            }
            return new ArtSourceSpec(Kind.REMOTE, trimmed, null, url);
        }
        String path = lower.startsWith(LOCAL_PREFIX) ? trimmed.substring(LOCAL_PREFIX.length()) : trimmed;
        String normalized = normalizeLocalPath(path);
        return new ArtSourceSpec(Kind.LOCAL, LOCAL_PREFIX + normalized, normalized, null);
    }

    public Path resolveLocalFile(Path imagesDir) throws ArtSourceException {
        if (kind != Kind.LOCAL) {
            throw new ArtSourceException("Not a local source: " + raw);
        }
        Path file = imagesDir.resolve(localPath)
            .normalize();
        Path base = imagesDir.normalize()
            .toAbsolutePath();
        Path resolved = file.toAbsolutePath();
        if (!resolved.equals(base) && !resolved.startsWith(base)) {
            throw new ArtSourceException("Local source escapes the images directory: " + localPath);
        }
        return file;
    }

    private static String normalizeLocalPath(String path) throws ArtSourceException {
        String normalized = path.replace('\\', '/')
            .trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new ArtSourceException("Empty local art path");
        }
        if (normalized.contains(":")) {
            throw new ArtSourceException("Local art path must be relative: " + path);
        }
        for (String part : normalized.split("/")) {
            if (part.equals("..")) {
                throw new ArtSourceException("Local art path must not contain '..': " + path);
            }
        }
        return normalized;
    }

    @Override
    public String toString() {
        return raw;
    }
}
