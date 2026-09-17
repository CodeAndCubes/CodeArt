package com.mrleonardos.codeart.network.s2c;

import com.mrleonardos.codeart.api.Hashes;
import com.mrleonardos.codeart.common.ArtBridge;
import com.mrleonardos.codeart.common.ClientArtSink;
import com.mrleonardos.codeart.network.ImageUnavailableReason;
import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.Codec;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;

/**
 * Отказ слать картинку: неизвестный хеш, троттлинг или сбой.
 *
 * <p>
 * Причина решает, повторит ли клиент запрос: троттлинг и сбой стоят в очереди заново, неизвестный хеш
 * гасит арт до пересборки реестра.
 */
public final class ImageUnavailablePacket extends Packet {

    private String sha256;
    private ImageUnavailableReason reason;

    public ImageUnavailablePacket() {}

    public ImageUnavailablePacket(String sha256, ImageUnavailableReason reason) {
        this.sha256 = sha256;
        this.reason = reason;
    }

    public String sha256() {
        return sha256;
    }

    public ImageUnavailableReason reason() {
        return reason;
    }

    @Override
    public void write(CodeBuffer buffer) {
        buffer.writeBytes(Hashes.fromHex(sha256));
        Codec.writeEnum(buffer, reason);
    }

    @Override
    public void read(CodeBuffer buffer) {
        byte[] raw = new byte[Hashes.SHA256_BYTES];
        buffer.readBytes(raw);
        sha256 = Hashes.toHex(raw);
        reason = Codec.readEnum(buffer, ImageUnavailableReason.class);
    }

    @Override
    public void handle(PacketContext context) {
        ClientArtSink sink = ArtBridge.client();
        if (sink != null) {
            sink.imageUnavailable(sha256, reason);
        }
    }
}
