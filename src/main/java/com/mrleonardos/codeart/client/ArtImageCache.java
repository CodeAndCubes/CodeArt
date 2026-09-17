package com.mrleonardos.codeart.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeart.api.ArtFormat;
import com.mrleonardos.codeart.api.Hashes;

/**
 * Локальный кэш полных картинок арта: файл по хешу, вытеснение давних по бюджету.
 *
 * <p>
 * Это не кэш ядра: тот держит приведённые png для интерфейса, а здесь лежат исходные байты арта, какие
 * прислал сервер, чтобы при повторном входе не качать их заново. Имя файла несёт хеш и расширение формата,
 * поэтому сверка целостности это один пересчёт sha256.
 */
public final class ArtImageCache {

    private static final Logger LOG = LogManager.getLogger(ArtImageCache.class);

    private final File directory;

    public ArtImageCache(File directory) {
        this.directory = directory;
        if (!directory.isDirectory() && !directory.mkdirs()) {
            LOG.error("Cannot create the art cache directory {}", directory.getAbsolutePath());
        }
    }

    public byte[] read(String sha256, ArtFormat format, int maxBytes) {
        File file = fileFor(sha256, format);
        if (!file.isFile() || file.length() > maxBytes) {
            return null;
        }
        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            LOG.warn("Cannot read cached art {}: {}", file.getName(), e.getMessage());
            return null;
        }
        if (!Hashes.sha256Hex(data)
            .equals(sha256)) {
            LOG.warn("Cached art {} is corrupted, dropping it", file.getName());
            if (!file.delete()) {
                LOG.warn("Cannot delete corrupted cache entry {}", file.getAbsolutePath());
            }
            return null;
        }
        if (!file.setLastModified(System.currentTimeMillis())) {
            LOG.debug("Cannot refresh the cache timestamp of {}", file.getName());
        }
        return data;
    }

    public void write(String sha256, ArtFormat format, byte[] data) {
        File file = fileFor(sha256, format);
        File temporary = new File(file.getAbsolutePath() + ".tmp");
        try {
            Files.write(temporary.toPath(), data);
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warn("Cannot cache art {}: {}", file.getName(), e.getMessage());
        }
    }

    public void enforceBudget(long budgetBytes) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        long total = 0L;
        List<File> candidates = new ArrayList<>(files.length);
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            total += file.length();
            candidates.add(file);
        }
        if (total <= budgetBytes) {
            return;
        }
        candidates.sort(Comparator.comparingLong(File::lastModified));
        for (File file : candidates) {
            if (total <= budgetBytes) {
                return;
            }
            long size = file.length();
            if (file.delete()) {
                total -= size;
            }
        }
    }

    private File fileFor(String sha256, ArtFormat format) {
        return new File(directory, sha256 + "." + format.extension());
    }
}
