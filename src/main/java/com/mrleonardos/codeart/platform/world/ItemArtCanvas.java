package com.mrleonardos.codeart.platform.world;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.mrleonardos.codeart.ArtConstants;
import com.mrleonardos.codeart.api.ArtApi;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.common.ArtBridge;
import com.mrleonardos.codeart.common.ClientArtSink;
import com.mrleonardos.codeart.internal.ArtMessages;
import com.mrleonardos.codecore.platform.ServerTexts;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Предмет-полотно: ставит арта на грань блока.
 *
 * <p>
 * Определение спрашивается у хранилища, а не хранится в предмете: NBT несёт только имя, и перевыпуск
 * арта тем же именем меняет картинку на уже поставленных полотнах. Строки игроку собирает сервер: файл
 * перевода у клиентской половины мода есть, но предмет ставится и в одиночной игре, где переводить
 * должен тот, кто показывает строку.
 */
public class ItemArtCanvas extends Item {

    public static final String NBT_ART = "Art";

    public ItemArtCanvas() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setUnlocalizedName(ArtConstants.MODID + ".art_canvas");
        setTextureName(ArtConstants.MODID + ":art_canvas");
        setCreativeTab(ArtCreativeTab.INSTANCE);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        String artName = ArtItems.artNameOf(stack);
        ArtDefinition definition = ArtApi.active()
            .map(store -> store.definition(artName))
            .orElse(null);
        if (definition == null) {
            player.addChatMessage(ServerTexts.line(ArtMessages.UNKNOWN, artName));
            return true;
        }

        ForgeDirection normal = ForgeDirection.getOrientation(side);
        if (normal == ForgeDirection.UNKNOWN) {
            return true;
        }
        int originX = x + normal.offsetX;
        int originY = y + normal.offsetY;
        int originZ = z + normal.offsetZ;
        int rotation = CanvasOrientation.rotationForPlacement(normal, player.rotationYaw);
        int[][] cells = ArtCanvasStructure
            .cells(originX, originY, originZ, normal, rotation, definition.widthBlocks(), definition.heightBlocks());

        for (int[] cell : cells) {
            if (!player.canPlayerEdit(cell[0], cell[1], cell[2], side, stack)) {
                player.addChatMessage(ServerTexts.line(ArtMessages.PLACE_DENIED));
                return true;
            }
        }
        if (!ArtCanvasStructure.canPlace(world, cells)) {
            player.addChatMessage(
                ServerTexts.line(ArtMessages.NO_SPACE, definition.widthBlocks(), definition.heightBlocks()));
            return true;
        }

        ArtCanvasStructure
            .place(world, cells, normal, rotation, artName, definition.widthBlocks(), definition.heightBlocks());
        Block.SoundType sound = ArtBlocks.canvas.stepSound;
        world.playSoundEffect(
            originX + 0.5D,
            originY + 0.5D,
            originZ + 0.5D,
            sound.func_150496_b(),
            (sound.getVolume() + 1.0F) / 2.0F,
            sound.getPitch() * 0.8F);
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
        }
        return true;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String artName = ArtItems.artNameOf(stack);
        if (artName.isEmpty()) {
            return StatCollector.translateToLocal(getUnlocalizedName() + ".name")
                .trim();
        }
        return artName;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        ClientArtSink sink = ArtBridge.client();
        if (sink == null) {
            return;
        }
        for (ArtDefinition definition : sink.creativeArts()) {
            ItemStack stack = ArtItems.createStack(definition, 1);
            if (stack != null) {
                list.add(stack);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        ClientArtSink sink = ArtBridge.client();
        ArtDefinition definition = sink == null ? null : sink.definition(ArtItems.artNameOf(stack));
        if (definition == null) {
            return;
        }
        lines.add(
            StatCollector.translateToLocalFormatted(
                ArtMessages.TOOLTIP_SIZE,
                definition.widthBlocks(),
                definition.heightBlocks()));
    }
}
