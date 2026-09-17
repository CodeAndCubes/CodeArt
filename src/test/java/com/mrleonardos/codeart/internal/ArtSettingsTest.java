package com.mrleonardos.codeart.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeart.api.ArtDefinition;

/**
 * Файл настроек читается и пишется настоящим ядром: формат, комментарии и порядок ключей это его
 * поведение. Значение за границами зажимается, а не отвергается: файл правит человек, и опечатка в
 * числе не должна выключать арты на сервере.
 */
class ArtSettingsTest {

    @TempDir
    Path root;

    @Test
    void theSettingsFileIsCreatedWithFactoryValues() {
        TestConfigs configs = TestConfigs.of(root);
        ArtSettings settings = configs.open(ArtSettings.spec())
            .get();

        assertEquals(16, settings.limits.canvasWidth());
        assertEquals(8 * 1024 * 1024, settings.limits.imageBytes());
        assertTrue(settings.sources.allowRemoteSources);
        assertFalse(settings.sources.allowDirectClientDownload);
        assertEquals(24_576, settings.network.chunkBytes());
    }

    @Test
    void theFileLivesInTheArtFolderOfTheLineup() {
        TestConfigs configs = TestConfigs.of(root);
        configs.open(ArtSettings.spec());

        Path file = configs.path(ArtSettings.spec());
        assertTrue(
            file.endsWith("art.toml"),
            () -> "файл настроек должен быть config/code/art/art.toml, получен " + file);
        assertTrue(java.nio.file.Files.exists(file), "файл создаётся при первом открытии");
    }

    @Test
    void valuesBeyondTheCeilingAreClampedOnRead() {
        TestConfigs.writeMain(root, "schemaVersion = 1");
        TestConfigs configs = TestConfigs.of(root);
        Path file = configs.path(ArtSettings.spec());
        TestConfigs.write(
            file,
            "schemaVersion = 1",
            "[limits]",
            "maxCanvasWidth = 500",
            "maxImageBytes = 999999999",
            "maxGifFrames = 0",
            "[sources]",
            "downloadTimeoutMillis = 1",
            "[network]",
            "chunkBytes = 999999");

        ArtSettings settings = configs.service()
            .open(ArtSettings.spec())
            .get();

        assertEquals(ArtDefinition.MAX_BLOCKS_PER_SIDE, settings.limits.canvasWidth());
        assertEquals(ArtDefinition.HARD_MAX_BYTES, settings.limits.imageBytes());
        assertEquals(ArtSettings.Limits.MIN_FRAMES, settings.limits.gifFrames());
        assertEquals(ArtSettings.Sources.MIN_TIMEOUT_MILLIS, settings.sources.downloadTimeoutMillis());
        assertEquals(ArtSettings.Network.MAX_CHUNK_BYTES, settings.network.chunkBytes());
    }

    @Test
    void aHandEditedFileKeepsItsValuesAndGainsNewKeys() {
        TestConfigs configs = TestConfigs.of(root);
        Path file = configs.path(ArtSettings.spec());
        TestConfigs.write(
            file,
            "schemaVersion = 1",
            "[limits]",
            "maxCanvasWidth = 32",
            "[sources]",
            "allowRemoteSources = false");

        ArtSettings settings = configs.service()
            .open(ArtSettings.spec())
            .get();

        assertEquals(32, settings.limits.canvasWidth());
        assertFalse(settings.sources.allowRemoteSources);
        assertTrue(
            TestConfigs.read(file)
                .contains("maxCanvasHeight"),
            "новые ключи дописываются в существующий файл");
    }
}
