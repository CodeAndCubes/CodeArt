package com.mrleonardos.codeart.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.mrleonardos.codeart.api.ArtStore;
import com.mrleonardos.codeart.api.Hashes;
import com.mrleonardos.codeart.network.ImageUnavailableReason;
import com.mrleonardos.codeart.network.s2c.ImageChunkPacket;
import com.mrleonardos.codeart.network.s2c.ImageUnavailablePacket;
import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.net.NetChannel;
import com.mrleonardos.codecore.api.util.Scheduler;

/**
 * Перекачка байтов картинки одному игроку: очередь, темп и троттлинг.
 *
 * <p>
 * Куски идут строго по порядку и не быстрее настройки: клиент собирает их подряд и сверяет номер, а
 * размер куска решает сервер. Несколько картинок одному игроку передаются одновременно до потолка
 * настройки, остальные ждут; просьбы сверх лимита в минуту получают отказ с троттлингом.
 *
 * <p>
 * Ни одного типа игры: игрок ездит ссылкой, байты уходят каналом ядра. Перекачка проверяется тестом без
 * запуска Minecraft.
 */
public final class ArtTransfers {

    private static final long RATE_WINDOW_MILLIS = 60_000L;

    private final Supplier<ArtStore> store;
    private final Supplier<ArtSettings> settings;
    private final NetChannel channel;
    private final Supplier<List<PlayerRef>> onlinePlayers;
    private final Scheduler scheduler;

    private final Map<UUID, PlayerTransfers> players = new ConcurrentHashMap<>();

    public ArtTransfers(Supplier<ArtStore> store, Supplier<ArtSettings> settings, NetChannel channel,
        Supplier<List<PlayerRef>> onlinePlayers, Scheduler scheduler) {
        this.store = store;
        this.settings = settings;
        this.channel = channel;
        this.onlinePlayers = onlinePlayers;
        this.scheduler = scheduler;
    }

    public void request(PlayerRef player, String sha256) {
        if (!Hashes.isSha256Hex(sha256)) {
            return;
        }
        ArtStore active = store.get();
        if (active == null) {
            unavailable(player, sha256, ImageUnavailableReason.SERVER_ERROR);
            return;
        }
        if (!active.isPublished(sha256)) {
            unavailable(player, sha256, ImageUnavailableReason.UNKNOWN_IMAGE);
            return;
        }
        PlayerTransfers transfers = players.get(player.id());
        if (transfers == null) {
            transfers = new PlayerTransfers();
            PlayerTransfers existing = players.putIfAbsent(player.id(), transfers);
            if (existing != null) {
                transfers = existing;
            }
        }
        transfers.enqueue(player, sha256);
    }

    /** Тик сервера: двинуть очереди и выкинуть ушедших. */
    public void tick() {
        if (players.isEmpty()) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (PlayerRef player : onlinePlayers.get()) {
            online.add(player.id());
            PlayerTransfers transfers = players.get(player.id());
            if (transfers != null) {
                transfers.tick(player);
            }
        }
        Iterator<Map.Entry<UUID, PlayerTransfers>> iterator = players.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            if (!online.contains(
                iterator.next()
                    .getKey())) {
                iterator.remove();
            }
        }
    }

    public void forget(UUID player) {
        players.remove(player);
    }

    public void clear() {
        players.clear();
    }

    private void unavailable(PlayerRef player, String sha256, ImageUnavailableReason reason) {
        channel.toPlayer(new ImageUnavailablePacket(sha256, reason), player);
    }

    private final class PlayerTransfers {

        private final Deque<String> pending = new ArrayDeque<>();
        private final Set<String> tracked = new HashSet<>();
        private final List<Transfer> active = new ArrayList<>();

        private long windowStart;
        private int windowCount;

        synchronized void enqueue(PlayerRef player, String sha256) {
            long now = System.currentTimeMillis();
            if (now - windowStart > RATE_WINDOW_MILLIS) {
                windowStart = now;
                windowCount = 0;
            }
            if (windowCount >= settings.get().network.imageRequestsPerMinute()) {
                unavailable(player, sha256, ImageUnavailableReason.THROTTLED);
                return;
            }
            windowCount++;
            if (!tracked.add(sha256)) {
                return;
            }
            pending.add(sha256);
        }

        synchronized void tick(PlayerRef player) {
            startTransfers();
            pushChunks(player);
        }

        private void startTransfers() {
            ArtStore activeStore = store.get();
            while (activeStore != null && !pending.isEmpty()
                && active.size() < settings.get().network.maxConcurrentTransfersPerPlayer()) {
                final String sha256 = pending.poll();
                final Transfer transfer = new Transfer(sha256);
                active.add(transfer);
                activeStore.execute(() -> {
                    final byte[] data = activeStore.content(sha256);
                    scheduler.onServerThread(() -> complete(transfer, data));
                });
            }
        }

        private synchronized void complete(Transfer transfer, byte[] data) {
            if (data == null) {
                transfer.failed = true;
                return;
            }
            transfer.data = data;
            transfer.chunkCount = Math
                .max(1, (data.length + settings.get().network.chunkBytes() - 1) / settings.get().network.chunkBytes());
        }

        private void pushChunks(PlayerRef player) {
            int budget = settings.get().network.chunksPerTickPerPlayer();
            Iterator<Transfer> iterator = active.iterator();
            while (iterator.hasNext()) {
                Transfer transfer = iterator.next();
                if (transfer.failed) {
                    unavailable(player, transfer.sha256, ImageUnavailableReason.SERVER_ERROR);
                    iterator.remove();
                    tracked.remove(transfer.sha256);
                    continue;
                }
                if (transfer.data == null) {
                    continue;
                }
                while (budget > 0 && transfer.nextChunk < transfer.chunkCount) {
                    int chunkBytes = settings.get().network.chunkBytes();
                    int offset = transfer.nextChunk * chunkBytes;
                    int length = Math.min(chunkBytes, transfer.data.length - offset);
                    byte[] payload = new byte[length];
                    System.arraycopy(transfer.data, offset, payload, 0, length);
                    channel.toPlayer(
                        new ImageChunkPacket(transfer.sha256, transfer.nextChunk, transfer.chunkCount, payload),
                        player);
                    transfer.nextChunk++;
                    budget--;
                }
                if (transfer.nextChunk >= transfer.chunkCount) {
                    iterator.remove();
                    tracked.remove(transfer.sha256);
                }
                if (budget <= 0) {
                    return;
                }
            }
        }
    }

    private static final class Transfer {

        private final String sha256;

        private byte[] data;
        private boolean failed;
        private int chunkCount;
        private int nextChunk;

        private Transfer(String sha256) {
            this.sha256 = sha256;
        }
    }
}
