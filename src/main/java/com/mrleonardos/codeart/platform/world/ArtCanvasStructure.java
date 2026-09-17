package com.mrleonardos.codeart.platform.world;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public final class ArtCanvasStructure {

    private ArtCanvasStructure() {}

    public static int[][] cells(int originX, int originY, int originZ, ForgeDirection normal, int rotation, int width,
        int height) {
        ForgeDirection right = CanvasOrientation.right(normal, rotation);
        ForgeDirection up = CanvasOrientation.up(normal, rotation);
        int[][] positions = new int[width * height][];
        int index = 0;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                positions[index++] = new int[] { originX + right.offsetX * column + up.offsetX * row,
                    originY + right.offsetY * column + up.offsetY * row,
                    originZ + right.offsetZ * column + up.offsetZ * row };
            }
        }
        return positions;
    }

    public static boolean canPlace(World world, int[][] cells) {
        for (int[] cell : cells) {
            if (cell[1] < 0 || cell[1] >= world.getHeight()) {
                return false;
            }
            if (!world.blockExists(cell[0], cell[1], cell[2])) {
                return false;
            }
            Block block = world.getBlock(cell[0], cell[1], cell[2]);
            if (!block.isReplaceable(world, cell[0], cell[1], cell[2])) {
                return false;
            }
        }
        return true;
    }

    public static void place(World world, int[][] cells, ForgeDirection normal, int rotation, String artName, int width,
        int height) {
        int[] origin = cells[0];
        int meta = normal.ordinal();

        world.setBlock(origin[0], origin[1], origin[2], ArtBlocks.canvas, meta, 3);
        TileEntity rootEntity = world.getTileEntity(origin[0], origin[1], origin[2]);
        if (rootEntity instanceof TileEntityArtCanvas) {
            TileEntityArtCanvas canvas = (TileEntityArtCanvas) rootEntity;
            canvas.configure(artName, normal, rotation, width, height);
            canvas.markDirty();
            world.markBlockForUpdate(origin[0], origin[1], origin[2]);
        }

        for (int i = 1; i < cells.length; i++) {
            int[] cell = cells[i];
            world.setBlock(cell[0], cell[1], cell[2], ArtBlocks.frame, meta, 3);
            TileEntity frameEntity = world.getTileEntity(cell[0], cell[1], cell[2]);
            if (frameEntity instanceof TileEntityArtFrame) {
                TileEntityArtFrame frame = (TileEntityArtFrame) frameEntity;
                frame.setRoot(origin[0], origin[1], origin[2]);
                frame.markDirty();
            }
        }
    }

    public static void clearFrames(World world, TileEntityArtCanvas canvas) {
        int[][] cells = cells(
            canvas.xCoord,
            canvas.yCoord,
            canvas.zCoord,
            canvas.normal(),
            canvas.rotation(),
            canvas.widthBlocks(),
            canvas.heightBlocks());
        for (int i = 1; i < cells.length; i++) {
            int[] cell = cells[i];
            if (cell[1] < 0 || cell[1] >= world.getHeight() || !world.blockExists(cell[0], cell[1], cell[2])) {
                continue;
            }
            if (world.getBlock(cell[0], cell[1], cell[2]) != ArtBlocks.frame) {
                continue;
            }
            TileEntity entity = world.getTileEntity(cell[0], cell[1], cell[2]);
            if (!(entity instanceof TileEntityArtFrame)) {
                continue;
            }
            TileEntityArtFrame frame = (TileEntityArtFrame) entity;
            if (frame.rootX() == canvas.xCoord && frame.rootY() == canvas.yCoord && frame.rootZ() == canvas.zCoord) {
                world.setBlockToAir(cell[0], cell[1], cell[2]);
            }
        }
    }

    public static TileEntityArtCanvas findRoot(World world, TileEntityArtFrame frame) {
        if (!frame.hasRoot() || !world.blockExists(frame.rootX(), frame.rootY(), frame.rootZ())) {
            return null;
        }
        if (world.getBlock(frame.rootX(), frame.rootY(), frame.rootZ()) != ArtBlocks.canvas) {
            return null;
        }
        TileEntity entity = world.getTileEntity(frame.rootX(), frame.rootY(), frame.rootZ());
        return entity instanceof TileEntityArtCanvas ? (TileEntityArtCanvas) entity : null;
    }
}
