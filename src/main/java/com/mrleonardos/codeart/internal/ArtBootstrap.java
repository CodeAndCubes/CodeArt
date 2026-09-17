package com.mrleonardos.codeart.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeart.ArtConstants;
import com.mrleonardos.codeart.api.ArtApi;
import com.mrleonardos.codeart.api.ArtStore;
import com.mrleonardos.codeart.internal.store.ArtStoreEvents;
import com.mrleonardos.codeart.internal.store.JsonArtStore;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.StorageSettings;
import com.mrleonardos.codecore.api.util.Scheduler;

/**
 * Как мод выбирает хранилище артов.
 *
 * <p>
 * Имя провайдера читается из секции {@code [storage]} главного файла линейки, с учётом отдельной секции
 * {@code [storage.art]}. Встроен {@code json}; чужие моды приносят свои регистрации в {@link ArtApi} на
 * своей инициализации, и реестр закрывается здесь же, в постинициализации.
 *
 * <p>
 * Незнакомое имя выключает хранилище со строкой в лог: тихий откат на встроенное запрещён, потому что
 * администратор, назвавший базу, должен получить базу, а не молчаливый файл рядом. Выключенное
 * хранилище оставляет команды: они отвечают готовой строкой о выключенном моде.
 */
public final class ArtBootstrap {

    private ArtBootstrap() {}

    /** Закрыть реестр провайдеров: позже постинициализации свои хранилища не приносят. */
    public static void freezeProviders() {
        ArtApi.freeze();
    }

    /**
     * Собрать хранилище по имени из главного файла.
     *
     * @return хранилище или пустая ссылка, когда имя не зарегистрировано и мод выключен
     */
    public static Optional<ArtStore> assemble(ConfigService configs, Supplier<ArtSettings> settings,
        Scheduler scheduler, ArtStoreEvents events, Logger log) {
        StorageSettings storage = configs.storage(ArtConstants.ROLE);
        String provider = storage.provider();
        Optional<ArtStore> foreign = ArtApi.store(provider);
        if (foreign.isPresent()) {
            log.info("Art store provider from the main config is {}", provider);
            return foreign;
        }
        if (!JsonArtStore.ID.equals(provider)) {
            log.warn(
                "Storage seam [storage] provider names {}, which is not registered (registered: {}); "
                    + "the art store is switched off: no registry, no images, no files",
                provider,
                registeredIds());
            return Optional.empty();
        }
        log.info("Art store provider from the main config is {}", provider);
        return Optional.of(new JsonArtStore(configs.directory(ArtConstants.ROLE), settings, scheduler, events, log));
    }

    private static List<String> registeredIds() {
        List<String> ids = new ArrayList<>();
        for (ArtStore store : ArtApi.stores()) {
            ids.add(store.id());
        }
        return ids;
    }
}
