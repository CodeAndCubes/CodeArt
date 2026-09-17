package com.mrleonardos.codeart.client;

import com.mrleonardos.codeart.ArtConstants;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;

/**
 * Содержимое {@code config/code/art/client/art-client.toml}: как картинки выглядят у игрока.
 *
 * <p>
 * Файл ваш: сервер сюда не заглядывает и ничего не навязывает. Работает, пока на сервере стоит CodeArt;
 * без него артов просто не будет.
 */
@Comment({ "Как клиент показывает арты. Файл ваш: сервер сюда не заглядывает.",
    "Пределы ходов в сеть живут в главном файле линейки, в секции [images]." })
public final class ArtClientSettings {

    public static final int MIN_PIXELS_PER_SIDE = 16;
    public static final int MAX_PIXELS_PER_SIDE = ArtDefinition.HARD_MAX_PIXELS_PER_SIDE;

    public static final int MIN_ACCEPTED_MB = 1;
    public static final int MAX_ACCEPTED_MB = ArtDefinition.HARD_MAX_BYTES / (1024 * 1024);
    public static final int MIN_CACHE_MB = 16;
    public static final int MAX_CACHE_MB = 16_384;
    public static final int MIN_TEXTURE_MB = 16;
    public static final int MAX_TEXTURE_MB = 4096;
    public static final int MIN_REQUESTS = 1;
    public static final int MAX_REQUESTS = 8;
    public static final int MIN_FRAMES = 1;
    public static final int MAX_FRAMES = ArtDefinition.HARD_MAX_FRAMES;

    private static final int BYTES_PER_MEGABYTE = 1024 * 1024;

    @Comment({ "Наибольшая картинка в мегабайтах, которую клиент соглашается принять.",
        "Больший арта показывается недоступным." })
    public int maxAcceptedImageMegabytes = 16;

    @Comment({ "Наибольшая сторона картинки в пикселях, которую клиент берётся разбирать.",
        "Злонамеренный манифест с гигантской стороной отвергается до скачивания и до выделения памяти." })
    public int maxPixelsPerSide = 8192;

    @Comment("Место на диске под локальный кэш полных картинок, в мегабайтах. Давние файлы вытесняются.")
    public int diskCacheBudgetMegabytes = 512;

    @Comment({ "Память под разобранные текстуры артов, в мегабайтах.",
        "Свыше потолка самые давние выгружаются и грузятся заново по надобности." })
    public int textureBudgetMegabytes = 256;

    @Comment("Сколько картинок клиент разбирает одновременно. Остальные ждут очереди.")
    public int maxConcurrentRequests = 3;

    @Comment({ "Наибольшее число кадров анимации, которые клиент разбирает. Лишние кадры отбрасываются." })
    public int maxFrames = 256;

    @Comment("Проигрывать анимацию. Выключено показывает первый кадр.")
    public boolean animate = true;

    @Comment({ "Фильтровать статичные арты плавно. Анимация всегда рисуется жёсткими пикселями:",
        " соседние кадры в атласе смешивались бы фильтром." })
    public boolean smoothScaling = true;

    public static ArtClientSettings defaults() {
        return new ArtClientSettings();
    }

    public static ConfigSpec<ArtClientSettings> spec() {
        return ConfigSpec.of(ArtConstants.MODID, "client", ArtClientSettings.class)
            .role(ConfigRoles.check(ArtConstants.ROLE))
            .scope(ConfigScope.CLIENT)
            .defaults(ArtClientSettings::defaults)
            .build();
    }

    public int maxPixelsPerSide() {
        return clamp(maxPixelsPerSide, MIN_PIXELS_PER_SIDE, MAX_PIXELS_PER_SIDE);
    }

    public int maxAcceptedImageBytes() {
        return clamp(maxAcceptedImageMegabytes, MIN_ACCEPTED_MB, MAX_ACCEPTED_MB) * BYTES_PER_MEGABYTE;
    }

    public long diskCacheBudgetBytes() {
        return (long) clamp(diskCacheBudgetMegabytes, MIN_CACHE_MB, MAX_CACHE_MB) * BYTES_PER_MEGABYTE;
    }

    public long textureBudgetBytes() {
        return (long) clamp(textureBudgetMegabytes, MIN_TEXTURE_MB, MAX_TEXTURE_MB) * BYTES_PER_MEGABYTE;
    }

    public int maxConcurrentRequests() {
        return clamp(maxConcurrentRequests, MIN_REQUESTS, MAX_REQUESTS);
    }

    public int maxFrames() {
        return clamp(maxFrames, MIN_FRAMES, MAX_FRAMES);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
