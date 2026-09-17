package com.mrleonardos.codeart.platform.world;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.mrleonardos.codeart.ArtConstants;

public final class ArtCreativeTab extends CreativeTabs {

    public static final ArtCreativeTab INSTANCE = new ArtCreativeTab();

    private ArtCreativeTab() {
        super(ArtConstants.MODID);
    }

    @Override
    public Item getTabIconItem() {
        return ArtItems.artCanvas;
    }
}
