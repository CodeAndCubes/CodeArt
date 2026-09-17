package com.mrleonardos.codeart.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtFormat;
import com.mrleonardos.codeart.api.Hashes;
import com.mrleonardos.codeart.network.s2c.ArtUpdatePacket;
import com.mrleonardos.codeart.network.s2c.ImageChunkPacket;
import com.mrleonardos.codeart.network.s2c.ManifestPacket;
import com.mrleonardos.codecore.api.net.ArrayBuffer;
import com.mrleonardos.codecore.api.net.MalformedPacketException;
import com.mrleonardos.codecore.api.net.Packet;

/**
 * Круговой прогон пакетов на буфере ядра: две стороны связывает только порядок полей, поэтому он и
 * проверяется. Подделанные длины и номера отвергаются до выделения памяти.
 */
class ArtPacketsTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void theImageChunkSurvivesTheRoundTrip() {
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        ImageChunkPacket read = roundTrip(new ImageChunkPacket(HASH, 3, 7, payload), new ImageChunkPacket());

        assertEquals(HASH, read.sha256());
        assertEquals(3, read.chunkIndex());
        assertEquals(7, read.chunkCount());
        assertArrayEquals(payload, read.payload());
    }

    @Test
    void aChunkIndexOutsideItsCountIsRejected() {
        ArrayBuffer buffer = new ArrayBuffer();
        buffer.writeBytes(Hashes.fromHex(HASH));
        buffer.writeInt(9);
        buffer.writeInt(7);
        buffer.writeInt(0);
        buffer.writeBytes(new byte[0]);

        ImageChunkPacket read = new ImageChunkPacket();
        assertThrows(MalformedPacketException.class, () -> read.read(buffer));
    }

    @Test
    void theManifestSurvivesTheRoundTrip() {
        List<ArtDefinition> definitions = new ArrayList<>();
        definitions.add(new ArtDefinition("poster", 4, 3, ArtFormat.GIF, HASH, 1024, 500, 400, 24, null));
        definitions
            .add(new ArtDefinition("mural", 1, 1, ArtFormat.PNG, Hashes.toHex(new byte[32]), 64, 16, 16, 1, null));
        ManifestPacket read = roundTrip(new ManifestPacket(true, true, definitions), new ManifestPacket());

        assertTrue(read.reset());
        assertTrue(read.last());
        assertEquals(definitions, read.definitions());
    }

    @Test
    void aManifestBatchBeyondTheCapIsRejected() {
        ArrayBuffer buffer = new ArrayBuffer();
        buffer.writeBoolean(true);
        buffer.writeBoolean(true);
        buffer.writeInt(ArtPackets.MANIFEST_BATCH + 1);

        ManifestPacket read = new ManifestPacket();
        assertThrows(MalformedPacketException.class, () -> read.read(buffer));
    }

    @Test
    void theUpdateCarriesBothKinds() {
        ArtDefinition definition = new ArtDefinition("poster", 2, 2, ArtFormat.PNG, HASH, 128, 32, 32, 1, null);

        ArtUpdatePacket readAdded = roundTrip(ArtUpdatePacket.added(definition), new ArtUpdatePacket());
        assertTrue(readAdded.added());
        assertEquals(definition, readAdded.definition());

        ArtUpdatePacket readRemoved = roundTrip(ArtUpdatePacket.removed("poster"), new ArtUpdatePacket());
        assertFalse(readRemoved.added());
        assertEquals("poster", readRemoved.name());
    }

    private static <T extends Packet> T roundTrip(T written, T empty) {
        ArrayBuffer buffer = new ArrayBuffer();
        written.write(buffer);
        empty.read(buffer);
        assertEquals(0, buffer.readableBytes(), "чтение обязано забрать ровно столько, сколько записала запись");
        return empty;
    }
}
