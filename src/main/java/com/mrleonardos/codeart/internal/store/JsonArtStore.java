package com.mrleonardos.codeart.internal.store;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtEntry;
import com.mrleonardos.codeart.api.ArtFormat;
import com.mrleonardos.codeart.api.ArtRecord;
import com.mrleonardos.codeart.api.ArtRegistry;
import com.mrleonardos.codeart.api.ArtStore;
import com.mrleonardos.codeart.api.Hashes;
import com.mrleonardos.codeart.internal.ArtMessages;
import com.mrleonardos.codeart.internal.ArtSettings;
import com.mrleonardos.codecore.api.http.HostPolicy;
import com.mrleonardos.codecore.api.http.HttpConnections;
import com.mrleonardos.codecore.api.image.ImageHeaders;
import com.mrleonardos.codecore.api.util.Scheduler;

/**
 * Встроенное хранилище: реестр в {@code arts.json}, байты картин файлами по хешу.
 *
 * <p>
 * Локальные источники читаются из {@code images} в папке настроек, удалённые скачиваются ходом ядра под его
 * политикой адресов и оседают в {@code cache}: после перезапуска сервер не ходит в сеть за тем же файлом. Метаданные,
 * которые сервер вычислил (хеш, размеры, кадры), дописываются в реестр рядом с источником, поэтому
 * повторный старт проверяет кэш по хешу, а не качает заново.
 *
 * <p>
 * Всё небыстрое живёт в двух рабочих потоках, ответы колбэкам и события реестра уходят в главный поток
 * через планировщик ядра: хранилище не решает за сервер, когда показывать строку игроку.
 */
public final class JsonArtStore implements ArtStore {

    public static final String ID = "json";

    private static final String IMAGE_ACCEPT = "image/png,image/jpeg,image/gif,image/*;q=0.8";

    private final Path rootDir;
    private final Path imagesDir;
    private final Path cacheDir;
    private final Path manifestFile;

    private final Supplier<ArtSettings> settings;
    private final Scheduler scheduler;
    private final ArtStoreEvents events;
    private final Logger log;

    private final ArtRegistry registry = new ArtRegistry();
    private final Map<String, ArtEntry> entries = new LinkedHashMap<>();
    private final Map<String, Path> contentFiles = new ConcurrentHashMap<>();
    private final Map<String, SoftReference<byte[]>> contentCache = new ConcurrentHashMap<>();

    private final ExecutorService worker;

    public JsonArtStore(Path rootDir, Supplier<ArtSettings> settings, Scheduler scheduler, ArtStoreEvents events,
        Logger log) {
        this.rootDir = rootDir;
        this.imagesDir = rootDir.resolve("images");
        this.cacheDir = rootDir.resolve("cache");
        this.manifestFile = rootDir.resolve("arts.json");
        this.settings = settings;
        this.scheduler = scheduler;
        this.events = events;
        this.log = log;
        this.worker = Executors.newFixedThreadPool(2, new ThreadFactory() {

            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "CodeArt-Store-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void start() {
        prepareDirectories();
        reload((success, messageKey, arguments) -> {
            if (ArtMessages.RELOAD_DONE.equals(messageKey)) {
                log.info("Art registry is loaded: {} definition(s), {} unavailable", arguments[0], arguments[1]);
            } else {
                log.warn("Art registry could not be read: {}", arguments.length > 0 ? arguments[0] : messageKey);
            }
        });
    }

    @Override
    public void reload(final Callback callback) {
        worker.execute(() -> {
            List<ArtRecord> records;
            try {
                records = ArtManifestFile.read(manifestFile, log);
            } catch (IOException e) {
                log.error("Cannot read {}", manifestFile, e);
                report(callback, false, ArtMessages.RELOAD_FAILED, String.valueOf(e.getMessage()));
                return;
            }
            final List<ArtEntry> resolved = new ArrayList<>(records.size());
            for (ArtRecord record : records) {
                resolved.add(resolve(record));
            }
            scheduler.onServerThread(() -> applyReload(resolved, callback));
        });
    }

    @Override
    public void add(final ArtRecord request, final Callback callback) {
        synchronized (this) {
            if (entries.containsKey(request.name())) {
                report(callback, false, ArtMessages.EXISTS, request.name());
                return;
            }
        }
        worker.execute(() -> {
            final ArtEntry entry = resolve(request);
            scheduler.onServerThread(() -> applyAdd(entry, callback));
        });
    }

    @Override
    public void remove(String name, Callback callback) {
        ArtEntry removed;
        synchronized (this) {
            removed = entries.remove(name);
        }
        if (removed == null) {
            report(callback, false, ArtMessages.UNKNOWN, name);
            return;
        }
        registry.remove(name);
        rebuildContentIndex();
        persistAsync();
        events.artRemoved(name);
        report(callback, true, ArtMessages.REMOVE_DONE, name);
    }

    @Override
    public synchronized List<ArtEntry> entries() {
        return new ArrayList<>(entries.values());
    }

    @Override
    public synchronized ArtEntry entry(String name) {
        return entries.get(name);
    }

    @Override
    public ArtDefinition definition(String name) {
        return registry.get(name);
    }

    @Override
    public boolean isPublished(String sha256) {
        return registry.containsHash(sha256);
    }

    @Override
    public byte[] content(String sha256) {
        SoftReference<byte[]> reference = contentCache.get(sha256);
        if (reference != null) {
            byte[] cached = reference.get();
            if (cached != null) {
                return cached;
            }
        }
        Path file = contentFiles.get(sha256);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        byte[] data;
        try {
            if (Files.size(file) > settings.get().limits.imageBytes()) {
                log.warn("Art content {} grew beyond the configured limit", file.getFileName());
                return null;
            }
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            log.error("Cannot read art content {}", file, e);
            return null;
        }
        if (!Hashes.sha256Hex(data)
            .equals(sha256)) {
            log.warn("Art content {} no longer matches its hash", file);
            return null;
        }
        contentCache.put(sha256, new SoftReference<>(data));
        return data;
    }

    @Override
    public void execute(Runnable task) {
        worker.execute(task);
    }

    @Override
    public void stop() {
        worker.shutdownNow();
        try {
            worker.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        synchronized (this) {
            entries.clear();
        }
        registry.clear();
        contentFiles.clear();
        contentCache.clear();
    }

    private void prepareDirectories() {
        mkdirs(rootDir);
        mkdirs(imagesDir);
        mkdirs(cacheDir);
    }

    private void mkdirs(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            log.error("Cannot create directory {}", directory, e);
        }
    }

    private void applyReload(List<ArtEntry> resolved, Callback callback) {
        List<ArtDefinition> definitions = new ArrayList<>(resolved.size());
        int failed = 0;
        synchronized (this) {
            entries.clear();
            for (ArtEntry entry : resolved) {
                entries.put(entry.name(), entry);
                if (entry.isReady()) {
                    definitions.add(entry.definition());
                } else {
                    failed++;
                    log.warn("Art '{}' is unavailable: {}", entry.name(), entry.error());
                }
            }
        }
        registry.replaceAll(definitions);
        rebuildContentIndex();
        persistAsync();
        events.registryReplaced();
        report(callback, true, ArtMessages.RELOAD_DONE, definitions.size(), failed);
    }

    private void applyAdd(ArtEntry entry, Callback callback) {
        if (!entry.isReady()) {
            report(callback, false, ArtMessages.ADD_FAILED, entry.name(), String.valueOf(entry.error()));
            return;
        }
        synchronized (this) {
            if (entries.containsKey(entry.name())) {
                report(callback, false, ArtMessages.EXISTS, entry.name());
                return;
            }
            entries.put(entry.name(), entry);
        }
        ArtDefinition definition = entry.definition();
        registry.put(definition);
        contentFiles.put(definition.sha256(), entry.contentFile());
        persistAsync();
        events.artAdded(definition);
        report(
            callback,
            true,
            ArtMessages.ADD_DONE,
            entry.name(),
            definition.pixelWidth(),
            definition.pixelHeight(),
            definition.frameCount());
    }

    private void rebuildContentIndex() {
        contentFiles.clear();
        for (ArtEntry entry : entries()) {
            if (entry.isReady()) {
                contentFiles.put(
                    entry.definition()
                        .sha256(),
                    entry.contentFile());
            }
        }
    }

    private void persistAsync() {
        final List<ArtRecord> records = new ArrayList<>();
        for (ArtEntry entry : entries()) {
            records.add(
                entry.isReady() ? entry.record()
                    .withMetadata(entry.definition()) : entry.record());
        }
        worker.execute(() -> {
            try {
                ArtManifestFile.write(manifestFile, records, log);
            } catch (IOException e) {
                log.error("Cannot write {}", manifestFile, e);
            }
        });
    }

    private ArtEntry resolve(ArtRecord record) {
        try {
            ArtSettings.Limits limits = settings.get().limits;
            if (record.widthBlocks() > limits.canvasWidth() || record.heightBlocks() > limits.canvasHeight()) {
                return ArtEntry.failed(
                    record,
                    "canvas " + record.widthBlocks()
                        + "x"
                        + record.heightBlocks()
                        + " exceeds the configured maximum of "
                        + limits.canvasWidth()
                        + "x"
                        + limits.canvasHeight());
            }
            ArtSourceSpec source = ArtSourceSpec.parse(record.source());
            return source.isRemote() ? resolveRemote(record, source) : resolveLocal(record, source);
        } catch (ArtSourceException e) {
            return ArtEntry.failed(record, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected failure while resolving art '{}'", record.name(), e);
            return ArtEntry.failed(record, "unexpected failure: " + e);
        }
    }

    private ArtEntry resolveLocal(ArtRecord record, ArtSourceSpec source) throws ArtSourceException {
        Path file = source.resolveLocalFile(imagesDir);
        if (!Files.isRegularFile(file)) {
            return ArtEntry.failed(record, "file not found: " + source.localPath());
        }
        ArtSettings.Limits limits = settings.get().limits;
        try {
            if (Files.size(file) > limits.imageBytes()) {
                return ArtEntry
                    .failed(record, "file is larger than the configured " + limits.imageBytes() + " byte limit");
            }
        } catch (IOException e) {
            return ArtEntry.failed(record, "cannot read file: " + e.getMessage());
        }
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            return ArtEntry.failed(record, "cannot read file: " + e.getMessage());
        }
        return describe(record, data, file, null);
    }

    private ArtEntry resolveRemote(ArtRecord record, ArtSourceSpec source) {
        ArtSettings settings = this.settings.get();
        if (!settings.sources.allowRemoteSources) {
            return ArtEntry.failed(record, "remote sources are disabled in the config");
        }
        String directUrl = settings.sources.allowDirectClientDownload ? source.url()
            .toString() : null;
        if (record.hasMetadata()) {
            Path cached = cacheFile(
                record.sha256(),
                record.format()
                    .extension());
            if (Files.isRegularFile(cached)) {
                try {
                    if (Files.size(cached) == record.byteSize()) {
                        byte[] data = Files.readAllBytes(cached);
                        if (Hashes.sha256Hex(data)
                            .equals(record.sha256())) {
                            return describe(record, data, cached, directUrl);
                        }
                    }
                } catch (IOException e) {
                    log.warn("Cannot read cached art {}: {}", cached.getFileName(), e.getMessage());
                }
            }
        }
        byte[] data;
        try {
            data = HttpConnections.read(
                source.url()
                    .toString(),
                HostPolicy.of(settings.sources.blockPrivateNetworks, settings.sources.allowedHosts),
                settings.limits.imageBytes(),
                IMAGE_ACCEPT,
                settings.sources.downloadTimeoutMillis(),
                settings.sources.downloadTimeoutMillis());
        } catch (IOException e) {
            return ArtEntry.failed(record, "download failed: " + e.getMessage());
        }
        ArtEntry described = describe(record, data, null, directUrl);
        if (!described.isReady()) {
            return described;
        }
        Path target = cacheFile(
            described.definition()
                .sha256(),
            described.definition()
                .format()
                .extension());
        try {
            storeAtomically(target, data);
        } catch (IOException e) {
            return ArtEntry.failed(record, "cannot cache image: " + e.getMessage());
        }
        return ArtEntry.ready(record, described.definition(), target);
    }

    private ArtEntry describe(ArtRecord record, byte[] data, Path contentFile, String directUrl) {
        ImageHeaders.Header header;
        try {
            header = ImageHeaders.read(data);
        } catch (IOException e) {
            return ArtEntry.failed(record, e.getMessage());
        }
        if (header == null) {
            return ArtEntry.failed(record, "Unsupported image format, expected PNG, JPEG or GIF");
        }
        ArtSettings.Limits limits = settings.get().limits;
        if (header.width() > limits.imagePixelsPerSide() || header.height() > limits.imagePixelsPerSide()) {
            return ArtEntry.failed(
                record,
                "image is " + header.width()
                    + "x"
                    + header.height()
                    + ", the configured maximum side is "
                    + limits.imagePixelsPerSide());
        }
        if (header.frameCount() > limits.gifFrames()) {
            return ArtEntry.failed(
                record,
                "image has " + header.frameCount() + " frames, the configured maximum is " + limits.gifFrames());
        }
        String sha256 = Hashes.sha256Hex(data);
        ArtDefinition definition = new ArtDefinition(
            record.name(),
            record.widthBlocks(),
            record.heightBlocks(),
            ArtFormat.valueOf(
                header.format()
                    .name()),
            sha256,
            data.length,
            header.width(),
            header.height(),
            header.frameCount(),
            directUrl);
        contentCache.put(sha256, new SoftReference<>(data));
        return ArtEntry.ready(record, definition, contentFile);
    }

    private Path cacheFile(String sha256, String extension) {
        return cacheDir.resolve(sha256 + "." + extension);
    }

    private static void storeAtomically(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, data);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void report(final Callback callback, final boolean success, final String messageKey,
        final Object... arguments) {
        scheduler.onServerThread(() -> callback.done(success, messageKey, arguments));
    }

}
