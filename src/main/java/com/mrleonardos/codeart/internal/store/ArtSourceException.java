package com.mrleonardos.codeart.internal.store;

public class ArtSourceException extends Exception {

    private static final long serialVersionUID = 1L;

    public ArtSourceException(String message) {
        super(message);
    }

    public ArtSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
