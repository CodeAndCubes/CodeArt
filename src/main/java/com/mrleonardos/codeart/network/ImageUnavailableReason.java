package com.mrleonardos.codeart.network;

/**
 * Почему сервер не может отдать картинку.
 *
 * <p>
 * Причина решает, повторит ли клиент запрос: троттлинг и сбой стоят в очереди заново, неизвестный хеш
 * гасит арт до пересборки реестра.
 */
public enum ImageUnavailableReason {

    UNKNOWN_IMAGE,
    THROTTLED,
    SERVER_ERROR;

    public boolean isRetryable() {
        return this == THROTTLED || this == SERVER_ERROR;
    }
}
