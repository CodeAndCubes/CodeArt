package com.mrleonardos.codeart.platform.world;

import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

public final class CanvasOrientation {

    public static final int ROTATION_COUNT = 4;

    private CanvasOrientation() {}

    public static ForgeDirection right(ForgeDirection normal, int rotation) {
        ForgeDirection direction = baseRight(normal);
        for (int i = 0; i < normalizeRotation(rotation); i++) {
            direction = cross(normal, direction);
        }
        return direction;
    }

    public static ForgeDirection up(ForgeDirection normal, int rotation) {
        ForgeDirection direction = baseUp(normal);
        for (int i = 0; i < normalizeRotation(rotation); i++) {
            direction = cross(normal, direction);
        }
        return direction;
    }

    public static int rotationForPlacement(ForgeDirection normal, float playerYaw) {
        if (normal != ForgeDirection.UP && normal != ForgeDirection.DOWN) {
            return 0;
        }
        ForgeDirection facing = horizontalFacing(playerYaw);
        for (int rotation = 0; rotation < ROTATION_COUNT; rotation++) {
            if (up(normal, rotation) == facing) {
                return rotation;
            }
        }
        return 0;
    }

    public static ForgeDirection horizontalFacing(float yaw) {
        int index = MathHelper.floor_double(yaw * 4.0F / 360.0F + 0.5D) & 3;
        switch (index) {
            case 0:
                return ForgeDirection.SOUTH;
            case 1:
                return ForgeDirection.WEST;
            case 2:
                return ForgeDirection.NORTH;
            default:
                return ForgeDirection.EAST;
        }
    }

    public static int normalizeRotation(int rotation) {
        return ((rotation % ROTATION_COUNT) + ROTATION_COUNT) % ROTATION_COUNT;
    }

    public static ForgeDirection normalFromMeta(int meta) {
        ForgeDirection direction = ForgeDirection.getOrientation(meta & 7);
        return direction == ForgeDirection.UNKNOWN ? ForgeDirection.NORTH : direction;
    }

    private static ForgeDirection baseUp(ForgeDirection normal) {
        if (normal == ForgeDirection.UP) {
            return ForgeDirection.NORTH;
        }
        if (normal == ForgeDirection.DOWN) {
            return ForgeDirection.SOUTH;
        }
        return ForgeDirection.UP;
    }

    private static ForgeDirection baseRight(ForgeDirection normal) {
        return cross(normal, baseUp(normal)).getOpposite();
    }

    private static ForgeDirection cross(ForgeDirection a, ForgeDirection b) {
        int x = a.offsetY * b.offsetZ - a.offsetZ * b.offsetY;
        int y = a.offsetZ * b.offsetX - a.offsetX * b.offsetZ;
        int z = a.offsetX * b.offsetY - a.offsetY * b.offsetX;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (direction.offsetX == x && direction.offsetY == y && direction.offsetZ == z) {
                return direction;
            }
        }
        return ForgeDirection.UNKNOWN;
    }
}
