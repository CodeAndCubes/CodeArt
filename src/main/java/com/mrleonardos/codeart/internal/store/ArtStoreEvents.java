package com.mrleonardos.codeart.internal.store;

import com.mrleonardos.codeart.api.ArtDefinition;

/**
 * Что хранилище сообщает миру при изменении реестра.
 *
 * <p>
 * Хранилище не знает ни сети, ни игроков: пересылкой манифеста занимается серверная обвязка, которая
 * подписывается сюда. Тестам достаточно реализации, которая всё пишет в список.
 */
public interface ArtStoreEvents {

    /** Реестр пересобран целиком: клиентам уходит свежий манифест. */
    void registryReplaced();

    /** Арт добавился или обновился. */
    void artAdded(ArtDefinition definition);

    /** Арт снят с реестра. */
    void artRemoved(String name);
}
