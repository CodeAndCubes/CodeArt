package com.mrleonardos.codeart.internal;

import java.util.ArrayList;
import java.util.List;

import com.mrleonardos.codeart.ArtConstants;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;

/**
 * Содержимое {@code config/code/art/art.toml}.
 *
 * <p>
 * Здесь потолки сервера: что вообще считается артом, откуда картинки приезжают и как быстро клиенту
 * отдаются байты. Как картинки выглядит сам клиент, решает его собственный файл
 * {@code config/code/art/client/art-client.toml}, и сервер в него не заглядывает. Провайдер хранилища
 * задаётся в главном файле линейки, во втором месте его нет.
 */
@Comment({ "Настройки артов сервера. Как картинки выглядят у игрока, решает файл клиента",
    "config/code/art/client/art-client.toml. Провайдер хранилища и автосейв живут в главном файле",
    "линейки config/code/config.toml, в секции [storage] и [storage.art]." })
public final class ArtSettings {

    public Limits limits = new Limits();
    public Sources sources = new Sources();
    public Network network = new Network();

    public static ArtSettings defaults() {
        return new ArtSettings();
    }

    public static ConfigSpec<ArtSettings> spec() {
        return ConfigSpec.settings(ArtConstants.MODID, ArtSettings.class)
            .role(ConfigRoles.check(ArtConstants.ROLE))
            .scope(ConfigScope.SETTINGS)
            .defaults(ArtSettings::defaults)
            .build();
    }

    /** Потолки того, что сервер называет артом. */
    public static final class Limits {

        /** Границы стороны полотна в блоках. */
        public static final int MIN_SIDE = 1;
        public static final int MAX_SIDE = ArtDefinition.MAX_BLOCKS_PER_SIDE;

        /** Границы файла картинки. */
        public static final int MIN_IMAGE_BYTES = 1024;
        public static final int MAX_IMAGE_BYTES = ArtDefinition.HARD_MAX_BYTES;

        /** Границы стороны картинки в пикселях. */
        public static final int MIN_PIXELS_PER_SIDE = 16;
        public static final int MAX_PIXELS_PER_SIDE = ArtDefinition.HARD_MAX_PIXELS_PER_SIDE;

        /** Границы числа кадров анимации. */
        public static final int MIN_FRAMES = 1;
        public static final int MAX_FRAMES = ArtDefinition.HARD_MAX_FRAMES;

        @Comment({ "Наибольшая ширина полотна в блоках, которую сервер согласен зарегистрировать.",
            "Выше жёсткого потолка в 64 блока значение не поднимается." })
        public int maxCanvasWidth = 16;

        @Comment("Наибольшая высота полотна в блоках.")
        public int maxCanvasHeight = 16;

        @Comment({ "Наибольший файл картинки в байтах: сервер принимает его, хранит и отдаёт клиентам.",
            "Выше жёсткого потолка в 64 мегабайта значение не поднимается." })
        public int maxImageBytes = 8 * 1024 * 1024;

        @Comment({ "Наибольшая сторона картинки в пикселях. Ограничивает и ширину, и высоту.",
            "Защита от картинок, разворачивающихся в тысячи мегапикселей." })
        public int maxImagePixelsPerSide = 8192;

        @Comment({ "Наибольшее число кадров анимации. Лишние кадры не грузятся вовсе.",
            "Выше жёсткого потолка в 4096 кадров значение не поднимается." })
        public int maxGifFrames = 256;

        public int canvasWidth() {
            return clamp(maxCanvasWidth, MIN_SIDE, MAX_SIDE);
        }

        public int canvasHeight() {
            return clamp(maxCanvasHeight, MIN_SIDE, MAX_SIDE);
        }

        public int imageBytes() {
            return clamp(maxImageBytes, MIN_IMAGE_BYTES, MAX_IMAGE_BYTES);
        }

        public int imagePixelsPerSide() {
            return clamp(maxImagePixelsPerSide, MIN_PIXELS_PER_SIDE, MAX_PIXELS_PER_SIDE);
        }

        public int gifFrames() {
            return clamp(maxGifFrames, MIN_FRAMES, MAX_FRAMES);
        }
    }

    /** Откуда серверу можно брать картинки. */
    public static final class Sources {

        public static final int MIN_TIMEOUT_MILLIS = 1000;
        public static final int MAX_TIMEOUT_MILLIS = 120_000;

        @Comment({ "Разрешить источники http(s) вдобавок к файлам из config/code/art/images.",
            "Локальные источники работают всегда." })
        public boolean allowRemoteSources = true;

        @Comment({ "Разрешить клиенту тянуть картину прямо с её источника, минуя сервер.",
            "Экономит канал сервера, но показывает хосту адреса игроков." })
        public boolean allowDirectClientDownload;

        @Comment({ "Отказываться от источников, которые решаются в петлю, локальную или приватную сеть.",
            "Выключать стоит только на доверенном сервере: адрес приходит от того, кто вводит команду." })
        public boolean blockPrivateNetworks = true;

        @Comment({ "Список разрешённых хостов для http(s)-источников. Пустой список разрешает любые.",
            "Запись с точкой впереди открывает и поддомены: .example.com открывает и example.com,",
            "и img.example.com." })
        public List<String> allowedHosts = new ArrayList<>();

        @Comment({ "Сколько миллисекунд ждать соединения и данных при скачивании источника сервером." })
        public int downloadTimeoutMillis = 15_000;

        public int downloadTimeoutMillis() {
            return clamp(downloadTimeoutMillis, MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS);
        }
    }

    /** Темп, в котором сервер отдаёт байты клиентам. */
    public static final class Network {

        public static final int MIN_CHUNK_BYTES = 1024;
        public static final int MAX_CHUNK_BYTES = 30_000;
        public static final int MIN_CHUNKS_PER_TICK = 1;
        public static final int MAX_CHUNKS_PER_TICK = 32;
        public static final int MIN_TRANSFERS = 1;
        public static final int MAX_TRANSFERS = 8;
        public static final int MIN_REQUESTS_PER_MINUTE = 1;
        public static final int MAX_REQUESTS_PER_MINUTE = 6000;

        @Comment({ "Размер одного пакета с куском картинки в байтах.",
            "Выше тридцати тысяч не поднимается: столько выдерживает ванильный клиентский канал." })
        public int chunkBytes = 24_576;

        @Comment("Сколько кусков одному игроку уходит за тик сервера.")
        public int chunksPerTickPerPlayer = 2;

        @Comment({ "Сколько картинок одному игроку передаётся одновременно. Остальные ждут очереди." })
        public int maxConcurrentTransfersPerPlayer = 2;

        @Comment({ "Сколько запросов картинок одному игроку разрешено в минуту. Дальше refusal с троттлингом." })
        public int imageRequestsPerMinute = 120;

        public int chunkBytes() {
            return clamp(chunkBytes, MIN_CHUNK_BYTES, MAX_CHUNK_BYTES);
        }

        public int chunksPerTickPerPlayer() {
            return clamp(chunksPerTickPerPlayer, MIN_CHUNKS_PER_TICK, MAX_CHUNKS_PER_TICK);
        }

        public int maxConcurrentTransfersPerPlayer() {
            return clamp(maxConcurrentTransfersPerPlayer, MIN_TRANSFERS, MAX_TRANSFERS);
        }

        public int imageRequestsPerMinute() {
            return clamp(imageRequestsPerMinute, MIN_REQUESTS_PER_MINUTE, MAX_REQUESTS_PER_MINUTE);
        }
    }

    static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
