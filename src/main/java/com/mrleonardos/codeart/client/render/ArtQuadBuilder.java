package com.mrleonardos.codeart.client.render;

import net.minecraftforge.common.util.ForgeDirection;

import com.mrleonardos.codeart.platform.world.BlockArtBase;

public final class ArtQuadBuilder {

    public static final double SURFACE_OFFSET = 0.001D;

    private ArtQuadBuilder() {}

    public static double baseCoordinate(int normalOffset, int rightOffset, int upOffset) {
        if (normalOffset > 0) {
            return BlockArtBase.THICKNESS + SURFACE_OFFSET;
        }
        if (normalOffset < 0) {
            return 1.0D - BlockArtBase.THICKNESS - SURFACE_OFFSET;
        }
        if (rightOffset != 0) {
            return rightOffset > 0 ? 0.0D : 1.0D;
        }
        if (upOffset != 0) {
            return upOffset > 0 ? 0.0D : 1.0D;
        }
        return 0.0D;
    }

    public static double[] origin(ForgeDirection normal, ForgeDirection right, ForgeDirection up) {
        return new double[] { baseCoordinate(normal.offsetX, right.offsetX, up.offsetX),
            baseCoordinate(normal.offsetY, right.offsetY, up.offsetY),
            baseCoordinate(normal.offsetZ, right.offsetZ, up.offsetZ) };
    }
}
