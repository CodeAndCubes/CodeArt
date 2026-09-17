package com.mrleonardos.codeart.client;

import java.io.File;
import java.util.List;

import net.minecraftforge.client.MinecraftForgeClient;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.client.render.ItemArtCanvasRenderer;
import com.mrleonardos.codeart.client.render.TileEntityArtCanvasRenderer;
import com.mrleonardos.codeart.common.ArtBridge;
import com.mrleonardos.codeart.common.ClientArtSink;
import com.mrleonardos.codeart.common.SideBootstrap;
import com.mrleonardos.codeart.network.ImageUnavailableReason;
import com.mrleonardos.codeart.platform.world.ArtItems;
import com.mrleonardos.codeart.platform.world.TileEntityArtCanvas;
import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.config.ConfigFile;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * Клиентская половина: рендеры, тик состояния и кэш.
 *
 * <p>
 * Строя её в своей фазе, мод регистрирует рендеры полотна и предмета, открывает собственный файл
 * настроек игрока и ставит сток пакетов. Выход с сервера сносит реестр и текстуры: на другом сервере
 * арты другие, а картина с прошлого висела бы заглушкой до первого манифеста.
 */
public final class ClientBootstrap implements SideBootstrap {

    private ConfigFile<ArtClientSettings> settings;

    @Override
    public void install(File gameDirectory) {
        settings = CodeApi.configs()
            .open(ArtClientSettings.spec());
        ClientArtState.initialize(gameDirectory, settings::get);
        ArtBridge.client(new Sink());

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityArtCanvas.class, new TileEntityArtCanvasRenderer());
        MinecraftForgeClient.registerItemRenderer(ArtItems.artCanvas, new ItemArtCanvasRenderer());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientArtState.tick();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientArtState.handleDisconnect();
    }

    private static final class Sink implements ClientArtSink {

        @Override
        public void manifest(boolean reset, boolean last, List<ArtDefinition> definitions) {
            ClientArtState.handleManifest(reset, last, definitions);
        }

        @Override
        public void artAdded(ArtDefinition definition) {
            ClientArtState.handleArtAdded(definition);
        }

        @Override
        public void artRemoved(String name) {
            ClientArtState.handleArtRemoved(name);
        }

        @Override
        public void imageChunk(String sha256, int chunkIndex, int chunkCount, byte[] payload) {
            ClientArtState.handleImageChunk(sha256, chunkIndex, chunkCount, payload);
        }

        @Override
        public void imageUnavailable(String sha256, ImageUnavailableReason reason) {
            ClientArtState.handleImageUnavailable(sha256, reason);
        }

        @Override
        public List<ArtDefinition> creativeArts() {
            return ClientArtState.registry()
                .all();
        }

        @Override
        public ArtDefinition definition(String name) {
            return ClientArtState.registry()
                .get(name);
        }
    }
}
