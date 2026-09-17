package com.mrleonardos.codeart.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeart.api.ArtEntry;
import com.mrleonardos.codeart.internal.ArtSettings;
import com.mrleonardos.codecore.api.util.Scheduler;

/**
 * Цикл пересборки хранилища: остановка мира глушит рабочие потоки, и следующий старт сервера обязан
 * поднять реестр заново тем же путём сборки. Это регрессия одиночной игры: выход в меню и загрузка
 * следующего мира не повторяют постинициализацию, и хранилище, не пересобранное на старте, оставляло бы
 * арты выключенными до перезапуска игры.
 */
class JsonArtStoreTest {

    @TempDir
    Path root;

    @BeforeEach
    void writeRegistry() throws IOException {
        Path images = root.resolve("images");
        Files.createDirectories(images);
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, 8, 8);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(
            image,
            "png",
            images.resolve("poster.png")
                .toFile());
        Files.write(
            root.resolve("arts.json"),
            ("{\"version\":1,\"arts\":[{\"name\":\"poster\",\"width\":1,\"height\":1,"
                + "\"source\":\"local://poster.png\"}]}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aFreshStoreLoadsTheRegistryAgainAfterThePreviousOneStopped() throws Exception {
        JsonArtStore first = new JsonArtStore(root, ArtSettings::defaults, direct(), events(), log());
        assertTrue(reloadCollects(first).isReady());
        first.stop();

        JsonArtStore second = new JsonArtStore(root, ArtSettings::defaults, direct(), events(), log());
        ArtEntry entry = reloadCollects(second);
        assertTrue(entry.isReady(), "пересборка тем же путём поднимает реестр прошлого мира");
        assertEquals("poster", entry.name());
        second.stop();
    }

    private ArtEntry reloadCollects(JsonArtStore store) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<ArtEntry>[] collected = new List[1];
        store.reload((success, messageKey, arguments) -> {
            collected[0] = store.entries();
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "колбэк перезагрузки не пришёл за отведённое время");
        assertEquals(1, collected[0].size());
        return collected[0].get(0);
    }

    private static Scheduler direct() {
        return new Scheduler() {

            @Override
            public void onMainThread(Runnable task) {
                task.run();
            }

            @Override
            public void afterTicks(int ticks, Runnable task) {
                task.run();
            }
        };
    }

    private static ArtStoreEvents events() {
        return new ArtStoreEvents() {

            @Override
            public void registryReplaced() {}

            @Override
            public void artAdded(com.mrleonardos.codeart.api.ArtDefinition definition) {}

            @Override
            public void artRemoved(String name) {}
        };
    }

    private static org.apache.logging.log4j.Logger log() {
        return LogManager.getLogger(JsonArtStoreTest.class);
    }
}
