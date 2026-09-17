package com.mrleonardos.codeart.platform.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

class ArtCanvasStructureTest {

    @Test
    void coversEveryCellExactlyOnce() {
        int[][] cells = ArtCanvasStructure.cells(10, 64, -3, ForgeDirection.SOUTH, 0, 4, 3);

        assertEquals(12, cells.length);
        Set<String> unique = new HashSet<>();
        for (int[] cell : cells) {
            unique.add(cell[0] + ":" + cell[1] + ":" + cell[2]);
        }
        assertEquals(12, unique.size());
    }

    @Test
    void startsAtTheClickedBlockAndStaysInThePlane() {
        int[][] cells = ArtCanvasStructure.cells(10, 64, -3, ForgeDirection.SOUTH, 0, 4, 3);

        assertArrayEquals(new int[] { 10, 64, -3 }, cells[0]);
        for (int[] cell : cells) {
            assertEquals(-3, cell[2], "a south facing canvas must stay on one z layer");
            assertTrue(cell[0] >= 10 && cell[0] <= 13, "canvas must grow east");
            assertTrue(cell[1] >= 64 && cell[1] <= 66, "canvas must grow up");
        }
    }

    @Test
    void floorCanvasStaysOnOneLayer() {
        int[][] cells = ArtCanvasStructure.cells(0, 70, 0, ForgeDirection.UP, 0, 3, 2);

        assertEquals(6, cells.length);
        for (int[] cell : cells) {
            assertEquals(70, cell[1], "a floor canvas must stay on one y layer");
        }
    }

    @Test
    void rotationTurnsTheGrowthDirections() {
        int[][] upright = ArtCanvasStructure.cells(0, 70, 0, ForgeDirection.UP, 0, 2, 1);
        int[][] rotated = ArtCanvasStructure.cells(0, 70, 0, ForgeDirection.UP, 1, 2, 1);

        assertArrayEquals(new int[] { 1, 70, 0 }, upright[1]);
        assertArrayEquals(new int[] { 0, 70, -1 }, rotated[1]);
    }

    @Test
    void everyOrientationKeepsARightHandedTriple() {
        for (ForgeDirection normal : ForgeDirection.VALID_DIRECTIONS) {
            for (int rotation = 0; rotation < CanvasOrientation.ROTATION_COUNT; rotation++) {
                ForgeDirection right = CanvasOrientation.right(normal, rotation);
                ForgeDirection up = CanvasOrientation.up(normal, rotation);
                assertEquals(normal, cross(right, up), "right x up must return the normal");
            }
        }
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
