package com.mrleonardos.codeart.common;

import java.util.List;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.network.ImageUnavailableReason;

/**
 * То, что приходящие пакеты делают на клиенте.
 *
 * <p>
 * Кроме сети сюда же смотрит общая половина мира предметов: творческая вкладка полотен и подсказка
 * спрашивают реестр артов, который живёт только в клиентском jar. Общий код видит этот интерфейс, а не
 * сам реестр.
 */
public interface ClientArtSink {

    /** Сервер прислал порцию манифеста. */
    void manifest(boolean reset, boolean last, List<ArtDefinition> definitions);

    /** Арт появился или обновился. */
    void artAdded(ArtDefinition definition);

    /** Арт снят с сервера. */
    void artRemoved(String name);

    /** Очередной кусок картинки: строго по порядку от нуля. */
    void imageChunk(String sha256, int chunkIndex, int chunkCount, byte[] payload);

    /** Сервер отказался слать картинку. */
    void imageUnavailable(String sha256, ImageUnavailableReason reason);

    /** Определения для творческой вкладки: всё, что клиент знает на этот момент. */
    List<ArtDefinition> creativeArts();

    /** Определение по имени или {@code null}. */
    ArtDefinition definition(String name);
}
