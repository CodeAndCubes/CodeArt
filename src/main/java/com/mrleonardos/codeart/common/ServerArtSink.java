package com.mrleonardos.codeart.common;

import com.mrleonardos.codecore.api.actor.PlayerRef;

/**
 * То, что приходящие пакеты делают на сервере.
 *
 * <p>
 * Серверная половина поднимается и на выделенном сервере, и во встроенном мире одиночной игры, поэтому
 * пакеты клиента находят её всегда, когда сервер вообще запущен.
 */
public interface ServerArtSink {

    /** Игрок просит байты картинки по хешу. */
    void requestImage(PlayerRef player, String sha256);
}
