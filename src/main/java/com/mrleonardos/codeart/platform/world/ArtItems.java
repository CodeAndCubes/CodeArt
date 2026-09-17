package com.mrleonardos.codeart.platform.world;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.mrleonardos.codeart.api.ArtDefinition;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ArtItems {

    public static ItemArtCanvas artCanvas;

    private ArtItems() {}

    public static void register() {
        artCanvas = new ItemArtCanvas();
        GameRegistry.registerItem(artCanvas, "art_canvas");
    }

    public static ItemStack createStack(String artName, int count) {
        if (artName == null || artName.isEmpty() || artCanvas == null) {
            return null;
        }
        ItemStack stack = new ItemStack(artCanvas, count);
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString(ItemArtCanvas.NBT_ART, artName);
        stack.setTagCompound(compound);
        return stack;
    }

    public static ItemStack createStack(ArtDefinition definition, int count) {
        return definition == null ? null : createStack(definition.name(), count);
    }

    public static String artNameOf(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return "";
        }
        return stack.getTagCompound()
            .getString(ItemArtCanvas.NBT_ART);
    }
}
