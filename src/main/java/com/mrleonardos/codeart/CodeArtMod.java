package com.mrleonardos.codeart;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeart.common.SideBootstrap;
import com.mrleonardos.codeart.network.ArtPackets;
import com.mrleonardos.codeart.platform.ArtServer;
import com.mrleonardos.codeart.platform.world.ArtBlocks;
import com.mrleonardos.codeart.platform.world.ArtItems;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

/**
 * Точка входа мода.
 *
 * <p>
 * Мод двусторонний, и клиент обязателен: {@code acceptableRemoteVersions} здесь нет и быть не должно.
 * Полотно без клиентской половины это пустой блок, игрок не видит картину и спорит с админом словами, а
 * сервер продолжал бы принимать подключения, которые никогда не покажут арта.
 *
 * <p>
 * Порядок фаз жёсткий. В {@code preInit} открывается канал и регистрируются блоки с предметами. В
 * {@code init} уходят корни команд и подписки на события. В {@code postInit}, когда чужие моды уже
 * принесли свои хранилища, реестр провайдеров закрывается и выбирается действующий. Реестр артов
 * поднимается на старте сервера.
 */
@Mod(
    modid = ArtConstants.MODID,
    name = ArtConstants.MOD_NAME,
    version = Tags.VERSION,
    dependencies = ArtConstants.DEPENDENCIES)
public final class CodeArtMod {

    public static final Logger LOG = LogManager.getLogger(ArtConstants.MOD_NAME);

    @SidedProxy(
        clientSide = "com.mrleonardos.codeart.client.ClientBootstrap",
        serverSide = "com.mrleonardos.codeart.common.HeadlessBootstrap")
    public static SideBootstrap side;

    private ArtServer server;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ArtPackets.register();
        ArtBlocks.register();
        ArtItems.register();
        side.install(gameDirectory(event));
        LOG.info("CodeArt {} is starting up", Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        server = new ArtServer();
        server.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        server.postInit();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        server.serverStarting();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        server.serverStopping();
    }

    private static File gameDirectory(FMLPreInitializationEvent event) {
        return event.getModConfigurationDirectory()
            .getParentFile();
    }
}
