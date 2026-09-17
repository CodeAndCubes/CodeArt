package com.mrleonardos.codeart.platform;

import java.util.Collections;

import net.minecraft.entity.player.EntityPlayerMP;

import com.mrleonardos.codeart.CodeArtMod;
import com.mrleonardos.codeart.api.ArtApi;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtStore;
import com.mrleonardos.codeart.common.ArtBridge;
import com.mrleonardos.codeart.internal.ArtBootstrap;
import com.mrleonardos.codeart.internal.ArtSettings;
import com.mrleonardos.codeart.internal.ArtTransfers;
import com.mrleonardos.codeart.internal.store.ArtStoreEvents;
import com.mrleonardos.codeart.network.ArtPackets;
import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.platform.PlayerRefs;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Серверная половина: хранилище артов, перекачка байтов и события.
 *
 * <p>
 * Поднимается и на выделенном сервере, и во встроенном мире одиночной игры, поэтому живёт в общем слое, а
 * не в вырезаемом: в клиентском jar её вырезать нельзя, иначе одиночная игра останется без артов.
 *
 * <p>
 * Хранилище появляется в постинициализации, останавливается вместе с миром и собирается заново на старте
 * следующего: {@link ArtApi} показывает наружу ровно то, что работает сейчас.
 */
public final class ArtServer {

    private ConfigFile<ArtSettings> settingsFile;
    private ArtStore store;

    private final ArtTransfers transfers = new ArtTransfers(
        () -> store,
        this::settings,
        ArtPackets.channel(),
        () -> CodeApi.players()
            .online(),
        CodeApi.scheduler());

    /** Действующие настройки или заводские, пока файл не открыт. */
    private ArtSettings settings() {
        return settingsFile == null ? ArtSettings.defaults() : settingsFile.get();
    }

    /** Хранилище или {@code null}, пока не собрано или выключено. */
    public ArtStore store() {
        return store;
    }

    public void init() {
        settingsFile = CodeApi.configs()
            .open(ArtSettings.spec());
        ArtBridge.server(this::requestImage);
        CodeApi.commands()
            .register(new ArtCommands(() -> store, this::settings).root());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ArtApi.install(() -> store);
    }

    public void postInit() {
        ArtBootstrap.freezeProviders();
        store = assembleStore();
    }

    /**
     * Старт сервера собирает хранилище заново, если прошлый мир его остановил.
     *
     * <p>
     * В одиночной игре выход в меню останавливает мир, а загрузка следующего не повторяет
     * постинициализацию: без пересборки хранилище оставалось бы выключенным до перезапуска игры, и арт
     * отвечал бы «провайдер не зарегистрирован» на исправном конфиге. Реестр провайдеров к этому моменту
     * заморожен, но заморозка закрывает его только для новых имён: чтение зарегистрированных работает.
     */
    public void serverStarting() {
        if (store == null) {
            store = assembleStore();
        }
        if (store != null) {
            store.start();
        }
    }

    public void serverStopping() {
        transfers.clear();
        if (store != null) {
            store.stop();
            store = null;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            transfers.tick();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (store == null) {
            return;
        }
        if (event.player instanceof EntityPlayerMP) {
            PlayerRef player = PlayerRefs.of((EntityPlayerMP) event.player);
            ArtPackets.sendManifest(store, Collections.singletonList(player));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        transfers.forget(event.player.getUniqueID());
    }

    private ArtStore assembleStore() {
        return ArtBootstrap
            .assemble(CodeApi.configs(), this::settings, CodeApi.scheduler(), broadcastEvents(), CodeArtMod.LOG)
            .orElse(null);
    }

    private void requestImage(PlayerRef player, String sha256) {
        transfers.request(player, sha256);
    }

    private ArtStoreEvents broadcastEvents() {
        return new ArtStoreEvents() {

            @Override
            public void registryReplaced() {
                ArtPackets.broadcastManifest(store);
            }

            @Override
            public void artAdded(ArtDefinition definition) {
                ArtPackets.broadcastAdded(definition);
            }

            @Override
            public void artRemoved(String name) {
                ArtPackets.broadcastRemoved(name);
            }
        };
    }
}
