package com.mrleonardos.codeart.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.net.ArrayBuffer;
import com.mrleonardos.codecore.api.net.Codec;
import com.mrleonardos.codecore.api.net.MalformedPacketException;

class ArtDefinitionTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void survivesNetworkRoundTrip() {
        ArtDefinition original = new ArtDefinition("poster", 4, 3, ArtFormat.GIF, HASH, 1024, 500, 400, 24, null);

        ArrayBuffer buffer = new ArrayBuffer();
        original.write(buffer);
        ArtDefinition restored = ArtDefinition.read(buffer);

        assertEquals(original, restored);
        assertEquals(0, buffer.readableBytes());
        assertTrue(restored.isAnimated());
        assertNull(restored.directUrl());
    }

    @Test
    void keepsDirectUrlAcrossTheNetwork() {
        ArtDefinition original = new ArtDefinition(
            "mural",
            1,
            1,
            ArtFormat.PNG,
            HASH,
            64,
            16,
            16,
            1,
            "https://example.invalid/a.png");

        ArrayBuffer buffer = new ArrayBuffer();
        original.write(buffer);
        ArtDefinition restored = ArtDefinition.read(buffer);

        assertEquals("https://example.invalid/a.png", restored.directUrl());
        assertFalse(restored.isAnimated());
    }

    @Test
    void rejectsTheDefinitionThatFailedValidationOnTheWire() {
        ArrayBuffer buffer = new ArrayBuffer();
        Codec.writeString(buffer, "poster");
        buffer.writeInt(4);
        buffer.writeInt(3);
        Codec.writeEnum(buffer, ArtFormat.PNG);
        buffer.writeBytes(Hashes.fromHex(HASH));
        buffer.writeInt(64);
        buffer.writeInt(ArtDefinition.HARD_MAX_PIXELS_PER_SIDE + 1);
        buffer.writeInt(16);
        buffer.writeInt(1);
        Codec.writeOptionalString(buffer, null);

        assertThrows(MalformedPacketException.class, () -> ArtDefinition.read(buffer));
    }

    @Test
    void acceptsOnlyLowerCaseNames() {
        assertTrue(ArtDefinition.isValidName("nature.sunset-01"));
        assertFalse(ArtDefinition.isValidName("Nature"));
        assertFalse(ArtDefinition.isValidName(".leading"));
        assertFalse(ArtDefinition.isValidName("with space"));
        assertFalse(ArtDefinition.isValidName(""));
        assertFalse(ArtDefinition.isValidName(null));
    }

    @Test
    void rejectsCanvasLargerThanTheHardCap() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ArtDefinition(
                "huge",
                ArtDefinition.MAX_BLOCKS_PER_SIDE + 1,
                1,
                ArtFormat.PNG,
                HASH,
                64,
                16,
                16,
                1,
                null));
    }

    @Test
    void rejectsMalformedHash() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ArtDefinition("bad", 1, 1, ArtFormat.PNG, "not-a-hash", 64, 16, 16, 1, null));
    }
}
