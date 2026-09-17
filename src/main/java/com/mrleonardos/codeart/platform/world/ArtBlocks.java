package com.mrleonardos.codeart.platform.world;

import com.mrleonardos.codeart.ArtConstants;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ArtBlocks {

    public static BlockArtCanvas canvas;
    public static BlockArtFrame frame;

    private ArtBlocks() {}

    public static void register() {
        canvas = new BlockArtCanvas();
        frame = new BlockArtFrame();
        GameRegistry.registerBlock(canvas, null, "art_canvas");
        GameRegistry.registerBlock(frame, null, "art_frame");
        GameRegistry.registerTileEntity(TileEntityArtCanvas.class, ArtConstants.MODID + ":art_canvas");
        GameRegistry.registerTileEntity(TileEntityArtFrame.class, ArtConstants.MODID + ":art_frame");
    }
}
