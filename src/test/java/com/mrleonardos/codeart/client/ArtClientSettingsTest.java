package com.mrleonardos.codeart.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.internal.TestConfigs;

/**
 * Клиентский файл тоже читается настоящим ядром: значения за границами зажимаются, ключи дописываются в
 * существующий файл.
 */
class ArtClientSettingsTest {

    @TempDir
    Path root;

    @Test
    void theClientFileIsCreatedWithFactoryValues() {
        ArtClientSettings settings = TestConfigs.of(root)
            .open(ArtClientSettings.spec())
            .get();

        assertEquals(16 * 1024 * 1024, settings.maxAcceptedImageBytes());
        assertEquals(8192, settings.maxPixelsPerSide());
        assertEquals(3, settings.maxConcurrentRequests());
    }

    @Test
    void thePixelSideIsClampedToTheHardCap() throws Exception {
        TestConfigs configs = TestConfigs.of(root);
        Path file = configs.path(ArtClientSettings.spec());
        TestConfigs.write(file, "maxPixelsPerSide = 999999");

        ArtClientSettings settings = configs.service()
            .open(ArtClientSettings.spec())
            .get();

        assertEquals(ArtDefinition.HARD_MAX_PIXELS_PER_SIDE, settings.maxPixelsPerSide());
    }
}
