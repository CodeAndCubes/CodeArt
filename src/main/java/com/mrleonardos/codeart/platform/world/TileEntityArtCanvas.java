package com.mrleonardos.codeart.platform.world;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.mrleonardos.codeart.api.ArtDefinition;

public class TileEntityArtCanvas extends TileEntity {

    private static final String KEY_ART = "Art";
    private static final String KEY_FACING = "Facing";
    private static final String KEY_ROTATION = "Rotation";
    private static final String KEY_WIDTH = "Width";
    private static final String KEY_HEIGHT = "Height";

    private String artName = "";
    private int facing = ForgeDirection.NORTH.ordinal();
    private int rotation;
    private int widthBlocks = 1;
    private int heightBlocks = 1;

    public void configure(String artName, ForgeDirection normal, int rotation, int widthBlocks, int heightBlocks) {
        this.artName = artName == null ? "" : artName;
        this.facing = normal.ordinal();
        this.rotation = CanvasOrientation.normalizeRotation(rotation);
        this.widthBlocks = clampSide(widthBlocks);
        this.heightBlocks = clampSide(heightBlocks);
    }

    public String artName() {
        return artName;
    }

    public ForgeDirection normal() {
        return ForgeDirection.getOrientation(facing);
    }

    public int rotation() {
        return rotation;
    }

    public int widthBlocks() {
        return widthBlocks;
    }

    public int heightBlocks() {
        return heightBlocks;
    }

    public ForgeDirection rightDirection() {
        return CanvasOrientation.right(normal(), rotation);
    }

    public ForgeDirection upDirection() {
        return CanvasOrientation.up(normal(), rotation);
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        artName = compound.getString(KEY_ART);
        facing = compound.getByte(KEY_FACING) & 7;
        if (facing >= ForgeDirection.VALID_DIRECTIONS.length) {
            facing = ForgeDirection.NORTH.ordinal();
        }
        rotation = CanvasOrientation.normalizeRotation(compound.getByte(KEY_ROTATION));
        widthBlocks = clampSide(compound.getByte(KEY_WIDTH));
        heightBlocks = clampSide(compound.getByte(KEY_HEIGHT));
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString(KEY_ART, artName);
        compound.setByte(KEY_FACING, (byte) facing);
        compound.setByte(KEY_ROTATION, (byte) rotation);
        compound.setByte(KEY_WIDTH, (byte) widthBlocks);
        compound.setByte(KEY_HEIGHT, (byte) heightBlocks);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound compound = new NBTTagCompound();
        writeToNBT(compound);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, compound);
    }

    @Override
    public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
        if (worldObj != null) {
            worldObj.func_147479_m(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        ForgeDirection right = rightDirection();
        ForgeDirection up = upDirection();
        int farX = xCoord + right.offsetX * (widthBlocks - 1) + up.offsetX * (heightBlocks - 1);
        int farY = yCoord + right.offsetY * (widthBlocks - 1) + up.offsetY * (heightBlocks - 1);
        int farZ = zCoord + right.offsetZ * (widthBlocks - 1) + up.offsetZ * (heightBlocks - 1);
        return AxisAlignedBB.getBoundingBox(
            Math.min(xCoord, farX),
            Math.min(yCoord, farY),
            Math.min(zCoord, farZ),
            Math.max(xCoord, farX) + 1,
            Math.max(yCoord, farY) + 1,
            Math.max(zCoord, farZ) + 1);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        int longestSide = Math.max(widthBlocks, heightBlocks);
        double range = 64.0D + longestSide * 8.0D;
        return range * range;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }

    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y,
        int z) {
        return oldBlock != newBlock;
    }

    private static int clampSide(int value) {
        if (value < 1) {
            return 1;
        }
        return Math.min(value, ArtDefinition.MAX_BLOCKS_PER_SIDE);
    }
}
