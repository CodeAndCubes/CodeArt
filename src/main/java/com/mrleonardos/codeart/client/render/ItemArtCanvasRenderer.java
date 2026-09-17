package com.mrleonardos.codeart.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.client.ArtImageStatus;
import com.mrleonardos.codeart.client.ArtTexture;
import com.mrleonardos.codeart.client.ClientArtState;
import com.mrleonardos.codeart.platform.world.ArtItems;

public class ItemArtCanvasRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY || type == ItemRenderType.ENTITY
            || type == ItemRenderType.EQUIPPED
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        ArtDefinition definition = ClientArtState.registry()
            .get(ArtItems.artNameOf(stack));
        ArtTexture texture = ClientArtState.texture(definition, 0.0D);

        boolean inventory = type == ItemRenderType.INVENTORY;
        float span = inventory ? 16.0F : 1.0F;
        float aspect = definition == null ? 1.0F
            : (float) definition.widthBlocks() / Math.max(1, definition.heightBlocks());
        float drawWidth = aspect >= 1.0F ? span : span * aspect;
        float drawHeight = aspect >= 1.0F ? span / aspect : span;
        float offsetX = (span - drawWidth) / 2.0F;
        float offsetY = (span - drawHeight) / 2.0F;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (!inventory) {
            GL11.glTranslatef(
                type == ItemRenderType.ENTITY ? -0.5F : 0.0F,
                type == ItemRenderType.ENTITY ? -0.5F : 0.0F,
                0.0F);
        }

        if (texture != null) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            texture.bind();
            int frame = texture.isAnimated() ? texture.frameAt(System.currentTimeMillis()) : 0;
            drawTexturedQuad(
                offsetX,
                offsetY,
                drawWidth,
                drawHeight,
                texture.minU(frame),
                texture.minV(frame),
                texture.maxU(frame),
                texture.maxV(frame),
                inventory);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            drawPlaceholderQuad(offsetX, offsetY, drawWidth, drawHeight, definition, inventory);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    private static void drawTexturedQuad(float x, float y, float width, float height, float minU, float minV,
        float maxU, float maxV, boolean flipVertical) {
        float topV = flipVertical ? minV : maxV;
        float bottomV = flipVertical ? maxV : minV;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0D, minU, bottomV);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, maxU, bottomV);
        tessellator.addVertexWithUV(x + width, y, 0.0D, maxU, topV);
        tessellator.addVertexWithUV(x, y, 0.0D, minU, topV);
        tessellator.draw();
    }

    private static void drawPlaceholderQuad(float x, float y, float width, float height, ArtDefinition definition,
        boolean inventory) {
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
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(red, green, blue, inventory ? 1.0F : 0.6F);
        tessellator.addVertex(x, y + height, 0.0D);
        tessellator.addVertex(x + width, y + height, 0.0D);
        tessellator.addVertex(x + width, y, 0.0D);
        tessellator.addVertex(x, y, 0.0D);
        tessellator.draw();

        float progress = ClientArtState.progress(definition);
        if (progress <= 0.0F) {
            return;
        }
        float barHeight = height * 0.12F;
        float barWidth = width * progress;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(0.85F, 0.85F, 0.95F, 1.0F);
        tessellator.addVertex(x, y + height, 0.0D);
        tessellator.addVertex(x + barWidth, y + height, 0.0D);
        tessellator.addVertex(x + barWidth, y + height - barHeight, 0.0D);
        tessellator.addVertex(x, y + height - barHeight, 0.0D);
        tessellator.draw();
    }
}
