package com.mrleonardos.codeart.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeart.api.ArtApi;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtEntry;
import com.mrleonardos.codeart.api.ArtRecord;
import com.mrleonardos.codeart.api.ArtStore;
import com.mrleonardos.codeart.internal.store.ArtStoreEvents;
import com.mrleonardos.codeart.internal.store.JsonArtStore;
import com.mrleonardos.codecore.api.util.Scheduler;

/**
 * Шов хранилища: имя из главного файла выбирает провайдера, незнакомое имя выключает хранилище, тихого
 * отката на встроенное нет.
 *
 * <p>
 * Реестр провайдеров глобален на всю виртуальную машину, как и у соседей линейки, поэтому тесты не
 * зависят от порядка: выборка ничего не регистрирует, а вся жизнь реестра от регистрации до заморозки
 * проверяется одним методом подряд.
 */
class ArtBootstrapTest {

    @TempDir
    Path root;

    @Test
    void theBuiltInProviderIsChosenWhenTheMainFileIsSilent() {
        TestConfigs configs = TestConfigs.of(root);

        Optional<ArtStore> store = assemble(configs);

        assertTrue(store.isPresent());
        assertInstanceOf(JsonArtStore.class, store.get());
        assertEquals(
            "json",
            store.get()
                .id());
    }

    @Test
    void theRoleSectionOverridesTheCommonProvider() {
        TestConfigs.writeMain(
            root,
            "schemaVersion = 1",
            "[storage]",
            "provider = \"never-registered\"",
            "[storage.art]",
            "provider = \"json\"");
        TestConfigs configs = TestConfigs.of(root);

        Optional<ArtStore> store = assemble(configs);

        assertTrue(store.isPresent(), "секция роли решает, и чужое общее имя ей не мешает");
    }

    @Test
    void anUnknownProviderNameStandsTheStoreDown() {
        TestConfigs.writeMain(root, "schemaVersion = 1", "[storage]", "provider = \"mongo\"");
        TestConfigs configs = TestConfigs.of(root);

        Optional<ArtStore> store = assemble(configs);

        assertFalse(store.isPresent(), "незнакомое имя выключает хранилище, отката на встроенное нет");
    }

    @Test
    void aForeignProviderWinsByRegistrationAndTheRegistryClosesAfterwards() {
        TestConfigs.writeMain(root, "schemaVersion = 1", "[storage]", "provider = \"sql\"");
        TestConfigs configs = TestConfigs.of(root);

        ArtApi.registerStore(new StubStore("sql"));
        Optional<ArtStore> chosen = assemble(configs);
        assertTrue(chosen.isPresent());
        assertEquals(
            "sql",
            chosen.get()
                .id());

        ArtBootstrap.freezeProviders();
        assertThrows(IllegalStateException.class, () -> ArtApi.registerStore(new StubStore("later")));
        assertTrue(
            ArtApi.store("sql")
                .isPresent(),
            "заморозка закрывает реестр для новых, но не выметает зарегистрированных");
    }

    @Test
    void theActiveSupplierServesWhateverRunsNow() {
        ArtApi.install(() -> null);

        assertFalse(
            ArtApi.active()
                .isPresent());
        assertThrows(IllegalStateException.class, ArtApi::service);
    }

    private static Scheduler directScheduler() {
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

    private Optional<ArtStore> assemble(TestConfigs configs) {
        return ArtBootstrap.assemble(configs.service(), ArtSettings::defaults, directScheduler(), new ArtStoreEvents() {

            @Override
            public void registryReplaced() {}

            @Override
            public void artAdded(ArtDefinition definition) {}

            @Override
            public void artRemoved(String name) {}
        }, LogManager.getLogger(ArtBootstrapTest.class));
    }

    private static final class StubStore implements ArtStore {

        private final String id;

        StubStore(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void start() {}

        @Override
        public void reload(Callback callback) {}

        @Override
        public void add(ArtRecord record, Callback callback) {}

        @Override
        public void remove(String name, Callback callback) {}

        @Override
        public List<ArtEntry> entries() {
            return Collections.emptyList();
        }

        @Override
        public ArtEntry entry(String name) {
            return null;
        }

        @Override
        public ArtDefinition definition(String name) {
            return null;
        }

        @Override
        public boolean isPublished(String sha256) {
            return false;
        }

        @Override
        public byte[] content(String sha256) {
            return null;
        }

        @Override
        public void execute(Runnable task) {}

        @Override
        public void stop() {}
    }
}
