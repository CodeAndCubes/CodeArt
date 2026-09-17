package com.mrleonardos.codeart.network.c2s;

import com.mrleonardos.codeart.api.Hashes;
import com.mrleonardos.codeart.common.ArtBridge;
import com.mrleonardos.codeart.common.ServerArtSink;
import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;

/**
 * Просьба клиента слать картинку по хешу.
 *
 * <p>
 * Тридцать два байта и ничего больше: клиент уже знает размер из манифеста, а сервер помнит, какому арту
 * этот хеш принадлежит. Отправителя пакет не называет, сервер берёт его из соединения.
 */
public final class RequestImagePacket extends Packet {

    private String sha256;

    public RequestImagePacket() {}

    public RequestImagePacket(String sha256) {
        this.sha256 = sha256;
    }

    public String sha256() {
        return sha256;
    }

    @Override
    public void write(CodeBuffer buffer) {
        buffer.writeBytes(Hashes.fromHex(sha256));
    }

    @Override
    public void read(CodeBuffer buffer) {
        byte[] raw = new byte[Hashes.SHA256_BYTES];
        buffer.readBytes(raw);
        sha256 = Hashes.toHex(raw);
    }

    @Override
    public void handle(PacketContext context) {
        PlayerRef player = context.player()
            .orElse(null);
        ServerArtSink sink = ArtBridge.server();
        if (player != null && sink != null) {
            sink.requestImage(player, sha256);
        }
    }
}
