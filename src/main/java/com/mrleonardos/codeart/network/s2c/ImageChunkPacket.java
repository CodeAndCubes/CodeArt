package com.mrleonardos.codeart.network.s2c;

import com.mrleonardos.codeart.api.Hashes;
import com.mrleonardos.codeart.common.ArtBridge;
import com.mrleonardos.codeart.common.ClientArtSink;
import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.Codec;
import com.mrleonardos.codecore.api.net.MalformedPacketException;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;

/**
 * Один кусок байтов картинки.
 *
 * <p>
 * Куски приезжают строго по порядку, и клиент сверяет номер: размер куска задаёт сервер, и клиентский
 * конфиг на него не влияет. Хеш ездит сырыми байтами, а не строкой: тридцать два байта против
 * шестидесятичетырёх на каждый кусок.
 */
public final class ImageChunkPacket extends Packet {

    private String sha256;
    private int chunkIndex;
    private int chunkCount;
    private byte[] payload;

    public ImageChunkPacket() {}

    public ImageChunkPacket(String sha256, int chunkIndex, int chunkCount, byte[] payload) {
        this.sha256 = sha256;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.payload = payload;
    }

    public String sha256() {
        return sha256;
    }

    public int chunkIndex() {
        return chunkIndex;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public byte[] payload() {
        return payload;
    }

    @Override
    public void write(CodeBuffer buffer) {
        buffer.writeBytes(Hashes.fromHex(sha256));
        buffer.writeInt(chunkIndex);
        buffer.writeInt(chunkCount);
        Codec.writeBlob(buffer, payload);
    }

    @Override
    public void read(CodeBuffer buffer) {
        byte[] raw = new byte[Hashes.SHA256_BYTES];
        buffer.readBytes(raw);
        sha256 = Hashes.toHex(raw);
        chunkIndex = buffer.readInt();
        chunkCount = buffer.readInt();
        if (chunkCount < 1 || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new MalformedPacketException("Malformed image chunk index " + chunkIndex + "/" + chunkCount);
        }
        payload = Codec.readBlob(buffer);
    }

    @Override
    public void handle(PacketContext context) {
        ClientArtSink sink = ArtBridge.client();
        if (sink != null) {
            sink.imageChunk(sha256, chunkIndex, chunkCount, payload);
        }
    }
}
