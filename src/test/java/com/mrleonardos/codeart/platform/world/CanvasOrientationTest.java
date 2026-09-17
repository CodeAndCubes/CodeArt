package com.mrleonardos.codeart.platform.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

class CanvasOrientationTest {

    @Test
    void rotationWrapsAround() {
        assertEquals(0, CanvasOrientation.normalizeRotation(4));
        assertEquals(3, CanvasOrientation.normalizeRotation(-1));
        assertEquals(2, CanvasOrientation.normalizeRotation(10));
    }

    @Test
    void floorAndCeilingPickRotationFromPlayerYaw() {
        assertEquals(2, CanvasOrientation.rotationForPlacement(ForgeDirection.UP, 0.0F), "looks south, top to south");
        assertEquals(3, CanvasOrientation.rotationForPlacement(ForgeDirection.UP, -90.0F), "looks east, top to east");
        assertEquals(0, CanvasOrientation.rotationForPlacement(ForgeDirection.UP, 180.0F), "looks north, top to north");
        assertEquals(1, CanvasOrientation.rotationForPlacement(ForgeDirection.UP, 90.0F), "looks west, top to west");
    }

    @Test
    void sideFacesIgnoreTheRotationChoice() {
        for (float yaw : new float[] { 0.0F, 45.0F, -170.0F }) {
            assertEquals(0, CanvasOrientation.rotationForPlacement(ForgeDirection.NORTH, yaw));
        }
    }

    @Test
    void upStaysPerpendicularToTheNormalOnEveryRotation() {
        for (ForgeDirection normal : ForgeDirection.VALID_DIRECTIONS) {
            for (int rotation = 0; rotation < CanvasOrientation.ROTATION_COUNT; rotation++) {
                ForgeDirection right = CanvasOrientation.right(normal, rotation);
                ForgeDirection up = CanvasOrientation.up(normal, rotation);
                assertEquals(
                    0,
                    normal.offsetX * right.offsetX + normal.offsetY * right.offsetY + normal.offsetZ * right.offsetZ,
                    "right lies in the canvas plane");
                assertEquals(
                    0,
                    normal.offsetX * up.offsetX + normal.offsetY * up.offsetY + normal.offsetZ * up.offsetZ,
                    "up lies in the canvas plane");
                assertEquals(
                    0,
                    right.offsetX * up.offsetX + right.offsetY * up.offsetY + right.offsetZ * up.offsetZ,
                    "right and up stay perpendicular");
            }
        }
    }

    @Test
    void metaRoundTripsThroughTheNormal() {
        for (ForgeDirection normal : ForgeDirection.VALID_DIRECTIONS) {
            assertEquals(normal, CanvasOrientation.normalFromMeta(normal.ordinal()));
        }
        assertEquals(ForgeDirection.NORTH, CanvasOrientation.normalFromMeta(15));
    }
}
