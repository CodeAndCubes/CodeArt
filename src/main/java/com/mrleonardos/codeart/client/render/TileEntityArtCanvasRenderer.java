package com.mrleonardos.codeart.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.client.ArtImageStatus;
import com.mrleonardos.codeart.client.ArtTexture;
import com.mrleonardos.codeart.client.ClientArtState;
import com.mrleonardos.codeart.platform.world.TileEntityArtCanvas;

public class TileEntityArtCanvasRenderer extends TileEntitySpecialRenderer {

    private static final float PLACEHOLDER_ALPHA = 0.35F;
    private static final double BAR_OFFSET = 0.0006D;

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileEntityArtCanvas)) {
            return;
        }
        TileEntityArtCanvas canvas = (TileEntityArtCanvas) tile;
        if (canvas.artName()
            .isEmpty()) {
            return;
        }
        ArtDefinition definition = ClientArtState.registry()
            .get(canvas.artName());
        ArtTexture texture = ClientArtState.texture(definition, x * x + y * y + z * z);

        ForgeDirection normal = canvas.normal();
        ForgeDirection right = canvas.rightDirection();
        ForgeDirection up = canvas.upDirection();
        double[] origin = ArtQuadBuilder.origin(normal, right, up);

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.004F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (texture != null) {
            renderArt(canvas, texture, origin, normal, right, up);
        } else {
            renderPlaceholder(canvas, definition, origin, normal, right, up);
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    private void renderArt(TileEntityArtCanvas canvas, ArtTexture texture, double[] origin, ForgeDirection normal,
        ForgeDirection right, ForgeDirection up) {
        int frame = texture.isAnimated() ? texture.frameAt(System.currentTimeMillis()) : 0;
        float minU = texture.minU(frame);
        float maxU = texture.maxU(frame);
        float minV = texture.minV(frame);
        float maxV = texture.maxV(frame);
        int width = canvas.widthBlocks();
        int height = canvas.heightBlocks();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        texture.bind();

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                tessellator.setBrightness(brightnessOf(canvas, normal, right, up, column, row));
                double baseX = origin[0] + right.offsetX * column + up.offsetX * row;
                double baseY = origin[1] + right.offsetY * column + up.offsetY * row;
                double baseZ = origin[2] + right.offsetZ * column + up.offsetZ * row;
                float u0 = interpolate(minU, maxU, column, width);
                float u1 = interpolate(minU, maxU, column + 1, width);
                float v0 = interpolate(minV, maxV, height - 1 - row, height);
                float v1 = interpolate(minV, maxV, height - row, height);

                tessellator.addVertexWithUV(baseX + up.offsetX, baseY + up.offsetY, baseZ + up.offsetZ, u0, v0);
                tessellator.addVertexWithUV(
                    baseX + right.offsetX + up.offsetX,
                    baseY + right.offsetY + up.offsetY,
                    baseZ + right.offsetZ + up.offsetZ,
                    u1,
                    v0);
                tessellator
                    .addVertexWithUV(baseX + right.offsetX, baseY + right.offsetY, baseZ + right.offsetZ, u1, v1);
                tessellator.addVertexWithUV(baseX, baseY, baseZ, u0, v1);
            }
        }
        tessellator.draw();
    }

    private void renderPlaceholder(TileEntityArtCanvas canvas, ArtDefinition definition, double[] origin,
        ForgeDirection normal, ForgeDirection right, ForgeDirection up) {
        ArtImageStatus status = ClientArtState.status(definition);
        float red = 0.35F;
        float green = 0.35F;
        float blue = 0.40F;
        if (definition == null || status == ArtImageStatus.FAILED) {
            red = 0.55F;
            green = 0.18F;
            blue = 0.18F;
        } else if (status == ArtImageStatus.PENDING) {
            red = 0.45F;
            green = 0.45F;
            blue = 0.20F;
        }

        int width = canvas.widthBlocks();
        int height = canvas.heightBlocks();
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(red, green, blue, PLACEHOLDER_ALPHA);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                tessellator.setBrightness(brightnessOf(canvas, normal, right, up, column, row));
                double baseX = origin[0] + right.offsetX * column + up.offsetX * row;
                double baseY = origin[1] + right.offsetY * column + up.offsetY * row;
                double baseZ = origin[2] + right.offsetZ * column + up.offsetZ * row;
                tessellator.addVertex(baseX + up.offsetX, baseY + up.offsetY, baseZ + up.offsetZ);
                tessellator.addVertex(
                    baseX + right.offsetX + up.offsetX,
                    baseY + right.offsetY + up.offsetY,
                    baseZ + right.offsetZ + up.offsetZ);
                tessellator.addVertex(baseX + right.offsetX, baseY + right.offsetY, baseZ + right.offsetZ);
                tessellator.addVertex(baseX, baseY, baseZ);
            }
        }
        tessellator.draw();
        renderProgressBar(canvas, definition, origin, normal, right, up, width, height);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void renderProgressBar(TileEntityArtCanvas canvas, ArtDefinition definition, double[] origin,
        ForgeDirection normal, ForgeDirection right, ForgeDirection up, int width, int height) {
        float progress = ClientArtState.progress(definition);
        if (progress <= 0.0F) {
            return;
        }
        double filled = width * progress;
        double thickness = Math.min(0.2D, height * 0.15D);
        double baseX = origin[0] + normal.offsetX * BAR_OFFSET;
        double baseY = origin[1] + normal.offsetY * BAR_OFFSET;
        double baseZ = origin[2] + normal.offsetZ * BAR_OFFSET;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setBrightness(brightnessOf(canvas, normal, right, up, 0, 0));
        tessellator.setColorRGBA_F(0.85F, 0.85F, 0.95F, 0.85F);
        tessellator
            .addVertex(baseX + up.offsetX * thickness, baseY + up.offsetY * thickness, baseZ + up.offsetZ * thickness);
        tessellator.addVertex(
            baseX + right.offsetX * filled + up.offsetX * thickness,
            baseY + right.offsetY * filled + up.offsetY * thickness,
            baseZ + right.offsetZ * filled + up.offsetZ * thickness);
        tessellator
            .addVertex(baseX + right.offsetX * filled, baseY + right.offsetY * filled, baseZ + right.offsetZ * filled);
        tessellator.addVertex(baseX, baseY, baseZ);
        tessellator.draw();
    }

    private static int brightnessOf(TileEntityArtCanvas canvas, ForgeDirection normal, ForgeDirection right,
        ForgeDirection up, int column, int row) {
        World world = canvas.getWorldObj();
        if (world == null) {
            return 240;
        }
        int x = canvas.xCoord + right.offsetX * column + up.offsetX * row + normal.offsetX;
        int y = canvas.yCoord + right.offsetY * column + up.offsetY * row + normal.offsetY;
        int z = canvas.zCoord + right.offsetZ * column + up.offsetZ * row + normal.offsetZ;
        if (y < 0 || y >= world.getHeight()) {
            return 240;
        }
        return world.getLightBrightnessForSkyBlocks(x, y, z, 0);
    }

    private static float interpolate(float from, float to, int step, int steps) {
        return from + (to - from) * step / steps;
    }
}
