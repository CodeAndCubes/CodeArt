package com.mrleonardos.codeart.platform.world;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class TileEntityArtFrame extends TileEntity {

    private static final String KEY_ROOT_X = "RootX";
    private static final String KEY_ROOT_Y = "RootY";
    private static final String KEY_ROOT_Z = "RootZ";

    private int rootX;
    private int rootY = -1;
    private int rootZ;

    public void setRoot(int x, int y, int z) {
        this.rootX = x;
        this.rootY = y;
        this.rootZ = z;
    }

    public boolean hasRoot() {
        return rootY >= 0;
    }

    public int rootX() {
        return rootX;
    }

    public int rootY() {
        return rootY;
    }

    public int rootZ() {
        return rootZ;
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        rootX = compound.getInteger(KEY_ROOT_X);
        rootY = compound.hasKey(KEY_ROOT_Y) ? compound.getInteger(KEY_ROOT_Y) : -1;
        rootZ = compound.getInteger(KEY_ROOT_Z);
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger(KEY_ROOT_X, rootX);
        compound.setInteger(KEY_ROOT_Y, rootY);
        compound.setInteger(KEY_ROOT_Z, rootZ);
    }

    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y,
        int z) {
        return oldBlock != newBlock;
    }
}
