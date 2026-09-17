package com.mrleonardos.codeart;

/** Имена, из которых ядро складывает пути файлов и сеть мода. */
public final class ArtConstants {

    public static final String MODID = "codeart";

    /** Имя мода для FML и для логгера. */
    public static final String MOD_NAME = "CodeArt";

    /** Ядро обязано загрузиться раньше: мод сразу регистрирует канал и команды. */
    public static final String DEPENDENCIES = "required-after:codecore";

    /** Область настроек: {@code config/code/art}. */
    public static final String ROLE = "art";

    /** Реестр артов: {@code config/code/art/arts.json}. */
    public static final String ARTS_FILE = "arts.json";

    /** Локальные источники вида {@code local://путь}: {@code config/code/art/images}. */
    public static final String IMAGES_DIR = "images";

    /** Скачанные источники: {@code config/code/art/cache}. */
    public static final String CACHE_DIR = "cache";

    /** Клиентский кэш полных картинок: {@code <игра>/codeart/cache}. */
    public static final String CLIENT_CACHE_DIR = "codeart";

    private ArtConstants() {}
}
