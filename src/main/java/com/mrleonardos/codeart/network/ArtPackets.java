package com.mrleonardos.codeart.network;

import java.util.ArrayList;
import java.util.List;

import com.mrleonardos.codeart.ArtConstants;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtEntry;
import com.mrleonardos.codeart.api.ArtStore;
import com.mrleonardos.codeart.network.c2s.RequestImagePacket;
import com.mrleonardos.codeart.network.s2c.ArtUpdatePacket;
import com.mrleonardos.codeart.network.s2c.ImageChunkPacket;
import com.mrleonardos.codeart.network.s2c.ImageUnavailablePacket;
import com.mrleonardos.codeart.network.s2c.ManifestPacket;
import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.net.NetChannel;
import com.mrleonardos.codecore.api.net.PacketSide;

/**
 * Регистрация пакетов мода.
 *
 * <p>
 * Порядок задаёт их номера в протоколе, поэтому строки нельзя менять местами и нельзя вставлять новые
 * в середину, можно только дописывать в конец. Иначе клиент старой версии прочитает пакет не тем классом.
 */
public final class ArtPackets {

    /** Сколько определений ездит одним пакетом манифеста. */
    public static final int MANIFEST_BATCH = 200;

    private static NetChannel channel;

    private ArtPackets() {}

    public static void register() {
        channel = CodeApi.network()
            .open(ArtConstants.MODID);

        channel.register(ManifestPacket.class, PacketSide.CLIENT_BOUND);
        channel.register(ArtUpdatePacket.class, PacketSide.CLIENT_BOUND);
        channel.register(ImageChunkPacket.class, PacketSide.CLIENT_BOUND);
        channel.register(ImageUnavailablePacket.class, PacketSide.CLIENT_BOUND);
        channel.register(RequestImagePacket.class, PacketSide.SERVER_BOUND);
    }

    /** Канал мода. Доступен после {@link #register()}. */
    public static NetChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("Art packets are not registered yet");
        }
        return channel;
    }

    /** Манифест одному игроку: реестр целиком, порциями по {@link #MANIFEST_BATCH}. */
    public static void sendManifest(ArtStore store, Iterable<PlayerRef> players) {
        for (ManifestPacket packet : manifestPackets(store)) {
            channel().toPlayers(packet, players);
        }
    }

    /** Манифест всем, кто на сервере. */
    public static void broadcastManifest(ArtStore store) {
        for (ManifestPacket packet : manifestPackets(store)) {
            channel().toAll(packet);
        }
    }

    /** Один арт добавился или обновился. */
    public static void broadcastAdded(ArtDefinition definition) {
        channel().toAll(ArtUpdatePacket.added(definition));
    }

    /** Арт снят с реестра. */
    public static void broadcastRemoved(String name) {
        channel().toAll(ArtUpdatePacket.removed(name));
    }

    private static List<ManifestPacket> manifestPackets(ArtStore store) {
        List<ArtDefinition> definitions = new ArrayList<>();
        for (ArtEntry entry : store.entries()) {
            if (entry.isReady()) {
                definitions.add(entry.definition());
            }
        }
        List<ManifestPacket> packets = new ArrayList<>();
        if (definitions.isEmpty()) {
            packets.add(new ManifestPacket(true, true, definitions));
            return packets;
        }
        for (int start = 0; start < definitions.size(); start += MANIFEST_BATCH) {
            int end = Math.min(start + MANIFEST_BATCH, definitions.size());
            packets.add(
                new ManifestPacket(
                    start == 0,
                    end == definitions.size(),
                    new ArrayList<>(definitions.subList(start, end))));
        }
        return packets;
    }
}
