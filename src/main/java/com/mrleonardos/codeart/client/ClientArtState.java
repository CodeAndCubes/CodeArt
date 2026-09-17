package com.mrleonardos.codeart.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import com.mrleonardos.codeart.ArtConstants;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtRegistry;
import com.mrleonardos.codeart.api.Hashes;
import com.mrleonardos.codeart.network.ArtPackets;
import com.mrleonardos.codeart.network.ImageUnavailableReason;
import com.mrleonardos.codeart.network.c2s.RequestImagePacket;
import com.mrleonardos.codecore.api.client.image.ImageLimits;
import com.mrleonardos.codecore.api.http.HostPolicy;
import com.mrleonardos.codecore.api.http.HttpConnections;
import com.mrleonardos.codecore.api.image.ImageHeaders;

/**
 * Состояние артов клиента: реестр с сервера, загрузка байтов и память текстур.
 *
 * <p>
 * Что грузить дальше, решает спрос рендера: каждый кадр полотно заявляет себя с квадратом расстояния, тик
 * выбирает ближайшие. Спрос сбрасывается каждый тик, поэтому уехавшее из поля зрения полотно не занимает
 * слот загрузки. Загруженное ограничено бюджетом памяти: самые давние выгружаются и возвращаются по
 * надобности, дисковый кэш переживает перезапуск игры.
 *
 * <p>
 * Потоки разделены жёстко: сетевые обработчики только кладут данные в потокобезопасные структуры,
 * декодирование идёт в рабочих потоках, а OpenGL трогает только тик клиента.
 */
public final class ClientArtState {

    private static final Logger LOG = LogManager.getLogger(ClientArtState.class);

    private static final long REQUEST_TIMEOUT_MILLIS = 60_000L;
    private static final long RETRY_DELAY_MILLIS = 5_000L;
    private static final long CACHE_SWEEP_INTERVAL_MILLIS = 300_000L;
    private static final long TEXTURE_KEEP_ALIVE_MILLIS = 5_000L;
    private static final int DECODES_PER_TICK = 2;

    private static final ArtRegistry REGISTRY = new ArtRegistry();
    private static final Map<String, ImageEntry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<String, ArtTexture> TEXTURES = new HashMap<>();
    private static final Map<String, Demand> DEMAND = new HashMap<>();
    private static final Deque<ArtTexture> UPLOADS = new ArrayDeque<>();
    private static final Queue<Runnable> MAIN_THREAD_TASKS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();

    private static ExecutorService worker;
    private static ArtImageCache cache;
    private static Supplier<ArtClientSettings> settings = ArtClientSettings::defaults;
    private static int maxTextureSize;
    private static long textureBytes;
    private static long nextCacheSweepMillis;

    private ClientArtState() {}

    public static void initialize(File gameDirectory, Supplier<ArtClientSettings> clientSettings) {
        ImageIO.setUseCache(false);
        settings = clientSettings;
        cache = new ArtImageCache(new File(new File(gameDirectory, ArtConstants.CLIENT_CACHE_DIR), "cache"));
        worker = Executors.newFixedThreadPool(2, new ThreadFactory() {

            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "CodeArt-Client-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public static ArtRegistry registry() {
        return REGISTRY;
    }

    public static void handleManifest(boolean reset, boolean last, List<ArtDefinition> definitions) {
        if (reset) {
            REGISTRY.clear();
        }
        REGISTRY.addAll(definitions);
        if (last) {
            LOG.info("Received {} art definition(s) from the server", REGISTRY.size());
        }
    }

    public static void handleArtAdded(ArtDefinition definition) {
        REGISTRY.put(definition);
    }

    public static void handleArtRemoved(String name) {
        REGISTRY.remove(name);
    }

    public static void handleImageChunk(String sha256, int chunkIndex, int chunkCount, byte[] payload) {
        ImageEntry entry = ENTRIES.get(sha256);
        if (entry == null) {
            return;
        }
        final ArtDefinition definition = REGISTRY.anyByHash(sha256);
        if (definition == null) {
            return;
        }
        final byte[] complete;
        synchronized (entry) {
            if (entry.buffer == null) {
                if (chunkIndex != 0) {
                    return;
                }
                entry.buffer = new byte[definition.byteSize()];
                entry.chunkCount = chunkCount;
                entry.receivedChunks = 0;
                entry.receivedBytes = 0;
            }
            if (chunkIndex != entry.receivedChunks || chunkCount != entry.chunkCount) {
                entry.buffer = null;
                return;
            }
            if (entry.receivedBytes + payload.length > entry.buffer.length) {
                entry.buffer = null;
                release(entry, ArtImageStatus.FAILED);
                return;
            }
            System.arraycopy(payload, 0, entry.buffer, entry.receivedBytes, payload.length);
            entry.receivedBytes += payload.length;
            entry.receivedChunks++;
            entry.lastProgressMillis = System.currentTimeMillis();
            if (entry.receivedChunks < entry.chunkCount) {
                return;
            }
            complete = entry.buffer;
            entry.buffer = null;
        }
        submit(() -> acceptDownloadedImage(definition, entry, complete, true));
    }

    public static void handleImageUnavailable(String sha256, ImageUnavailableReason reason) {
        ImageEntry entry = ENTRIES.get(sha256);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            entry.buffer = null;
        }
        LOG.warn("Server cannot deliver art image {}: {}", sha256.substring(0, 8), reason);
        if (reason.isRetryable()) {
            entry.retryAfterMillis = System.currentTimeMillis() + RETRY_DELAY_MILLIS;
            release(entry, ArtImageStatus.ABSENT);
        } else {
            release(entry, ArtImageStatus.FAILED);
        }
    }

    public static void handleDisconnect() {
        REGISTRY.clear();
        ENTRIES.clear();
        MAIN_THREAD_TASKS.clear();
        IN_FLIGHT.set(0);
        MAIN_THREAD_TASKS.add(ClientArtState::disposeTextures);
    }

    private static void disposeTextures() {
        for (ArtTexture texture : TEXTURES.values()) {
            texture.dispose();
        }
        TEXTURES.clear();
        UPLOADS.clear();
        DEMAND.clear();
        textureBytes = 0L;
    }

    public static ArtTexture texture(ArtDefinition definition, double distanceSquared) {
        if (definition == null) {
            return null;
        }
        ArtTexture texture = TEXTURES.get(definition.sha256());
        if (texture != null) {
            texture.markUsed(System.currentTimeMillis());
            return texture.isReady() ? texture : null;
        }
        Demand demand = DEMAND.get(definition.sha256());
        if (demand == null) {
            DEMAND.put(definition.sha256(), new Demand(definition, distanceSquared));
        } else if (distanceSquared < demand.distanceSquared) {
            demand.distanceSquared = distanceSquared;
        }
        return null;
    }

    public static ArtImageStatus status(ArtDefinition definition) {
        if (definition == null) {
            return ArtImageStatus.ABSENT;
        }
        ArtTexture texture = TEXTURES.get(definition.sha256());
        if (texture != null) {
            return texture.isReady() ? ArtImageStatus.READY : ArtImageStatus.PENDING;
        }
        ImageEntry entry = ENTRIES.get(definition.sha256());
        return entry == null ? ArtImageStatus.ABSENT : entry.status;
    }

    public static float progress(ArtDefinition definition) {
        if (definition == null) {
            return 0.0F;
        }
        ArtTexture texture = TEXTURES.get(definition.sha256());
        if (texture != null) {
            return 0.5F + 0.5F * texture.uploadProgress();
        }
        ImageEntry entry = ENTRIES.get(definition.sha256());
        return entry == null ? 0.0F : 0.5F * entry.downloadProgress();
    }

    public static void tick() {
        if (maxTextureSize == 0) {
            maxTextureSize = Math.max(1024, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE));
        }
        for (int i = 0; i < DECODES_PER_TICK; i++) {
            Runnable task = MAIN_THREAD_TASKS.poll();
            if (task == null) {
                break;
            }
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.error("Art client task failed", e);
            }
        }
        advanceUploads();
        startDemandedLoads();
        long now = System.currentTimeMillis();
        expireStalledRequests(now);
        enforceTextureBudget(now);
        sweepDiskCache(now);
    }

    private static void advanceUploads() {
        ArtTexture texture = UPLOADS.peek();
        if (texture == null) {
            return;
        }
        texture.uploadStep();
        if (texture.isReady()) {
            UPLOADS.poll();
        }
    }

    private static void startDemandedLoads() {
        if (DEMAND.isEmpty()) {
            return;
        }
        List<Demand> demands = new ArrayList<>(DEMAND.values());
        DEMAND.clear();
        demands.sort((left, right) -> Double.compare(left.distanceSquared, right.distanceSquared));
        for (Demand demand : demands) {
            if (IN_FLIGHT.get() >= settings.get()
                .maxConcurrentRequests()) {
                return;
            }
            requestIfNeeded(demand.definition);
        }
    }

    private static void requestIfNeeded(ArtDefinition definition) {
        final ImageEntry entry = entryFor(definition.sha256());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            if (entry.status != ArtImageStatus.ABSENT || now < entry.retryAfterMillis) {
                return;
            }
            if (definition.byteSize() > settings.get()
                .maxAcceptedImageBytes()) {
                entry.status = ArtImageStatus.FAILED;
                LOG.warn(
                    "Art '{}' is {} bytes, above the configured client limit of {}",
                    definition.name(),
                    definition.byteSize(),
                    settings.get()
                        .maxAcceptedImageBytes());
                return;
            }
            if (definition.pixelWidth() > settings.get()
                .maxPixelsPerSide() || definition.pixelHeight()
                    > settings.get()
                        .maxPixelsPerSide()) {
                entry.status = ArtImageStatus.FAILED;
                LOG.warn(
                    "Art '{}' declares {}x{} pixels, above the configured client side limit of {}",
                    definition.name(),
                    definition.pixelWidth(),
                    definition.pixelHeight(),
                    settings.get()
                        .maxPixelsPerSide());
                return;
            }
            if (IN_FLIGHT.get() >= settings.get()
                .maxConcurrentRequests()) {
                return;
            }
            entry.status = ArtImageStatus.PENDING;
            entry.lastProgressMillis = now;
        }
        IN_FLIGHT.incrementAndGet();
        submit(() -> beginLoad(definition, entry));
    }

    private static void beginLoad(ArtDefinition definition, ImageEntry entry) {
        byte[] cached = cache.read(
            definition.sha256(),
            definition.format(),
            settings.get()
                .maxAcceptedImageBytes());
        if (cached != null) {
            acceptDownloadedImage(definition, entry, cached, false);
            return;
        }
        if (definition.directUrl() != null) {
            byte[] direct = fetchDirect(definition);
            if (direct != null) {
                acceptDownloadedImage(definition, entry, direct, true);
                return;
            }
        }
        entry.lastProgressMillis = System.currentTimeMillis();
        ArtPackets.channel()
            .toServer(new RequestImagePacket(definition.sha256()));
    }

    /**
     * Прямая загрузка с источника: чтение и политика адресов ядра, политику клиент собирает из секции
     * {@code [images]} главного файла линейки, потому что ход клиента в сеть это вопрос владельца
     * машины, а не сервера артов.
     */
    private static byte[] fetchDirect(ArtDefinition definition) {
        ImageLimits limits = ImageLimits.current();
        try {
            byte[] data = HttpConnections.read(
                definition.directUrl(),
                HostPolicy.of(limits.blockPrivateNetworks(), limits.allowedHosts()),
                settings.get()
                    .maxAcceptedImageBytes(),
                null,
                limits.connectTimeoutMs(),
                limits.readTimeoutMs());
            if (!Hashes.sha256Hex(data)
                .equals(definition.sha256())) {
                LOG.warn("Direct download of '{}' did not match the announced hash", definition.name());
                return null;
            }
            return data;
        } catch (IOException e) {
            LOG.warn("Direct download of '{}' failed: {}", definition.name(), e.getMessage());
            return null;
        }
    }

    private static void acceptDownloadedImage(ArtDefinition definition, ImageEntry entry, byte[] data,
        boolean storeInCache) {
        if (!Hashes.sha256Hex(data)
            .equals(definition.sha256())) {
            release(entry, ArtImageStatus.FAILED);
            return;
        }
        if (!matchesDefinition(definition, data)) {
            release(entry, ArtImageStatus.FAILED);
            return;
        }
        if (storeInCache) {
            cache.write(definition.sha256(), definition.format(), data);
        }
        ArtTexture texture;
        try {
            texture = ArtImageDecoder.decode(
                data,
                definition,
                maxTextureSize > 0 ? maxTextureSize : 4096,
                settings.get()
                    .maxFrames(),
                settings.get().animate,
                settings.get().smoothScaling);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot decode '{}': {}", definition.name(), e.toString());
            release(entry, ArtImageStatus.FAILED);
            return;
        } catch (Throwable e) {
            LOG.error("Decoding of '{}' killed the worker step", definition.name(), e);
            release(entry, ArtImageStatus.FAILED);
            return;
        }
        MAIN_THREAD_TASKS.add(() -> installTexture(definition, entry, texture));
    }

    /**
     * Байты сходятся с манифестом: формат и размеры из заголовка читает ядро, и расхождение означает
     * битую пересылку или чужой файл под знакомым хешем.
     */
    private static boolean matchesDefinition(ArtDefinition definition, byte[] data) {
        ImageHeaders.Header header;
        try {
            header = ImageHeaders.read(data);
        } catch (IOException e) {
            LOG.warn("Cannot read the header of '{}': {}", definition.name(), e.getMessage());
            return false;
        }
        if (header == null) {
            LOG.warn("'{}' is not a png, jpeg or gif image", definition.name());
            return false;
        }
        if (!header.format()
            .name()
            .equals(
                definition.format()
                    .name())
            || header.width() != definition.pixelWidth()
            || header.height() != definition.pixelHeight()) {
            LOG.warn(
                "'{}' on disk is {} {}x{}, the manifest says {} {}x{}",
                definition.name(),
                header.format(),
                header.width(),
                header.height(),
                definition.format(),
                definition.pixelWidth(),
                definition.pixelHeight());
            return false;
        }
        return true;
    }

    private static void installTexture(ArtDefinition definition, ImageEntry entry, ArtTexture texture) {
        texture.markUsed(System.currentTimeMillis());
        ArtTexture previous = TEXTURES.put(definition.sha256(), texture);
        if (previous != null) {
            textureBytes -= previous.memoryFootprint();
            UPLOADS.remove(previous);
            previous.dispose();
        }
        textureBytes += texture.memoryFootprint();
        UPLOADS.add(texture);
        release(entry, ArtImageStatus.READY);
    }

    private static void expireStalledRequests(long now) {
        for (ImageEntry entry : ENTRIES.values()) {
            if (entry.status == ArtImageStatus.PENDING && now - entry.lastProgressMillis > REQUEST_TIMEOUT_MILLIS) {
                synchronized (entry) {
                    entry.buffer = null;
                }
                release(entry, ArtImageStatus.ABSENT);
            }
        }
    }

    private static void enforceTextureBudget(long now) {
        long budget = settings.get()
            .textureBudgetBytes();
        if (textureBytes <= budget) {
            return;
        }
        List<Map.Entry<String, ArtTexture>> candidates = new ArrayList<>(TEXTURES.entrySet());
        candidates.sort(
            (left, right) -> Long.compare(
                left.getValue()
                    .lastUsedMillis(),
                right.getValue()
                    .lastUsedMillis()));
        for (Map.Entry<String, ArtTexture> candidate : candidates) {
            if (textureBytes <= budget) {
                return;
            }
            if (!candidate.getValue()
                .isReady()) {
                continue;
            }
            if (now - candidate.getValue()
                .lastUsedMillis() < TEXTURE_KEEP_ALIVE_MILLIS) {
                continue;
            }
            textureBytes -= candidate.getValue()
                .memoryFootprint();
            candidate.getValue()
                .dispose();
            TEXTURES.remove(candidate.getKey());
            ImageEntry entry = ENTRIES.get(candidate.getKey());
            if (entry != null) {
                entry.status = ArtImageStatus.ABSENT;
            }
        }
    }

    private static void sweepDiskCache(long now) {
        if (now < nextCacheSweepMillis || worker == null) {
            return;
        }
        nextCacheSweepMillis = now + CACHE_SWEEP_INTERVAL_MILLIS;
        submit(
            () -> cache.enforceBudget(
                settings.get()
                    .diskCacheBudgetBytes()));
    }

    private static ImageEntry entryFor(String sha256) {
        ImageEntry entry = ENTRIES.get(sha256);
        if (entry != null) {
            return entry;
        }
        ImageEntry created = new ImageEntry();
        ImageEntry existing = ENTRIES.putIfAbsent(sha256, created);
        return existing != null ? existing : created;
    }

    private static void release(ImageEntry entry, ArtImageStatus status) {
        boolean wasPending;
        synchronized (entry) {
            wasPending = entry.status == ArtImageStatus.PENDING;
            entry.status = status;
        }
        if (wasPending) {
            IN_FLIGHT.updateAndGet(value -> value > 0 ? value - 1 : 0);
        }
    }

    private static void submit(Runnable task) {
        ExecutorService executor = worker;
        if (executor == null) {
            return;
        }
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                LOG.error("Art client worker task failed, the step is dropped", e);
            }
        });
    }

    private static final class ImageEntry {

        private volatile ArtImageStatus status = ArtImageStatus.ABSENT;
        private volatile long lastProgressMillis;
        private volatile long retryAfterMillis;
        private volatile int chunkCount;
        private volatile int receivedChunks;

        private byte[] buffer;
        private int receivedBytes;

        private float downloadProgress() {
            int total = chunkCount;
            return total <= 0 ? 0.0F : Math.min(1.0F, (float) receivedChunks / total);
        }
    }

    private static final class Demand {

        private final ArtDefinition definition;

        private double distanceSquared;

        private Demand(ArtDefinition definition, double distanceSquared) {
            this.definition = definition;
            this.distanceSquared = distanceSquared;
        }
    }
}
