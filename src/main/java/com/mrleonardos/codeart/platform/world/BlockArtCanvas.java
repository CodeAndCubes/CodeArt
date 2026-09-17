package com.mrleonardos.codeart.platform.world;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.mrleonardos.codeart.ArtConstants;

public class BlockArtCanvas extends BlockArtBase {

    public BlockArtCanvas() {
        setBlockName(ArtConstants.MODID + ".art_canvas");
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileEntityArtCanvas();
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!world.isRemote && willHarvest && (player == null || !player.capabilities.isCreativeMode)) {
            TileEntity entity = world.getTileEntity(x, y, z);
            if (entity instanceof TileEntityArtCanvas) {
                ItemStack stack = ArtItems.createStack(((TileEntityArtCanvas) entity).artName(), 1);
                if (stack != null) {
                    dropBlockAsItem(world, x, y, z, stack);
                }
            }
        }
        return world.setBlockToAir(x, y, z);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntity entity = world.getTileEntity(x, y, z);
            if (entity instanceof TileEntityArtCanvas) {
                ArtCanvasStructure.clearFrames(world, (TileEntityArtCanvas) entity);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
