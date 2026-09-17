package com.mrleonardos.codeart.platform.world;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.mrleonardos.codeart.ArtConstants;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class BlockArtBase extends Block {

    public static final float THICKNESS = 1.0F / 16.0F;

    protected BlockArtBase() {
        super(Material.cloth);
        setHardness(0.3F);
        setResistance(1.0F);
        setStepSound(soundTypeCloth);
        setBlockTextureName(ArtConstants.MODID + ":canvas");
        setCreativeTab(null);
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getLightOpacity() {
        return 0;
    }

    @Override
    public int getMobilityFlag() {
        return 2;
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return null;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        return new ArrayList<>();
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        applyBounds(CanvasOrientation.normalFromMeta(world.getBlockMetadata(x, y, z)));
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getSelectedBoundingBoxFromPool(world, x, y, z);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(getTextureName());
    }

    protected void applyBounds(ForgeDirection normal) {
        float minX = 0.0F;
        float minY = 0.0F;
        float minZ = 0.0F;
        float maxX = 1.0F;
        float maxY = 1.0F;
        float maxZ = 1.0F;
        if (normal.offsetX > 0) {
            maxX = THICKNESS;
        } else if (normal.offsetX < 0) {
            minX = 1.0F - THICKNESS;
        } else if (normal.offsetY > 0) {
            maxY = THICKNESS;
        } else if (normal.offsetY < 0) {
            minY = 1.0F - THICKNESS;
        } else if (normal.offsetZ > 0) {
            maxZ = THICKNESS;
        } else if (normal.offsetZ < 0) {
            minZ = 1.0F - THICKNESS;
        }
        setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
