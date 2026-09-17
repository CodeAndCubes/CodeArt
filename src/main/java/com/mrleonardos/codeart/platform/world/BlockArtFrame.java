package com.mrleonardos.codeart.platform.world;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.mrleonardos.codeart.ArtConstants;

public class BlockArtFrame extends BlockArtBase {

    public BlockArtFrame() {
        setBlockName(ArtConstants.MODID + ".art_frame");
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileEntityArtFrame();
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (!world.isRemote) {
            TileEntityArtCanvas root = findRoot(world, x, y, z);
            if (root != null) {
                return ArtBlocks.canvas
                    .removedByPlayer(world, player, root.xCoord, root.yCoord, root.zCoord, willHarvest);
            }
        }
        return world.setBlockToAir(x, y, z);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntityArtCanvas root = findRoot(world, x, y, z);
            if (root != null) {
                world.setBlockToAir(root.xCoord, root.yCoord, root.zCoord);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    private static TileEntityArtCanvas findRoot(World world, int x, int y, int z) {
        TileEntity entity = world.getTileEntity(x, y, z);
        if (!(entity instanceof TileEntityArtFrame)) {
            return null;
        }
        return ArtCanvasStructure.findRoot(world, (TileEntityArtFrame) entity);
    }
}
