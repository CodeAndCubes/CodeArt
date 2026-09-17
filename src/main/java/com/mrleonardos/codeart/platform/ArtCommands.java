package com.mrleonardos.codeart.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.mrleonardos.codeart.ArtPermissions;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtEntry;
import com.mrleonardos.codeart.api.ArtRecord;
import com.mrleonardos.codeart.api.ArtStore;
import com.mrleonardos.codeart.internal.ArtMessages;
import com.mrleonardos.codeart.internal.ArtSettings;
import com.mrleonardos.codeart.platform.world.ArtItems;
import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.ArgumentTypes;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.platform.PlayerRefs;

/**
 * Дерево {@code /art}.
 *
 * <p>
 * Команды не решают ничего сами: они спрашивают хранилище и пересказывают ответ. Право сидит на каждой
 * ветке, поэтому недоступная ветка не появляется и в автодополнении, а уровень доступа ванильных команд
 * моду не нужен вовсе.
 *
 * <p>
 * Ответы асинхронных операций приходят ключом перевода: хранилище собирает их в рабочем потоке, а строку
 * по языку сервера собирает тот, кто её показывает.
 */
public final class ArtCommands {

    private static final String NAME = "name";
    private static final String PAGE = "page";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String SOURCE = "source";
    private static final String PLAYER = "player";
    private static final String COUNT = "count";
    private static final int PAGE_SIZE = 8;
    private static final int MAX_COUNT = 64;

    private final Supplier<ArtStore> store;
    private final Supplier<ArtSettings> settings;

    public ArtCommands(Supplier<ArtStore> store, Supplier<ArtSettings> settings) {
        this.store = store;
        this.settings = settings;
    }

    /** Корень команд мода. */
    public CommandNode root() {
        return CommandNode.literal("art")
            .usage(ArtMessages.USAGE_ROOT)
            .child(
                CommandNode.literal("list")
                    .usage(ArtMessages.USAGE_LIST)
                    .permission(ArtPermissions.VIEW)
                    .optionalArg(PAGE, ArgumentTypes.integer(1, Integer.MAX_VALUE))
                    .executes(this::list))
            .child(
                CommandNode.literal("info")
                    .usage(ArtMessages.USAGE_INFO)
                    .permission(ArtPermissions.VIEW)
                    .arg(NAME, artName())
                    .executes(this::info))
            .child(
                CommandNode.literal("add")
                    .usage(ArtMessages.USAGE_ADD)
                    .permission(ArtPermissions.ADMIN)
                    .arg(NAME, ArgumentTypes.word())
                    .arg(WIDTH, ArgumentTypes.integer(1, ArtDefinition.MAX_BLOCKS_PER_SIDE))
                    .arg(HEIGHT, ArgumentTypes.integer(1, ArtDefinition.MAX_BLOCKS_PER_SIDE))
                    .arg(SOURCE, ArgumentTypes.text())
                    .executes(this::add))
            .child(
                CommandNode.literal("remove")
                    .usage(ArtMessages.USAGE_REMOVE)
                    .permission(ArtPermissions.ADMIN)
                    .arg(NAME, artName())
                    .executes(this::remove))
            .child(
                CommandNode.literal("reload")
                    .permission(ArtPermissions.ADMIN)
                    .executes(this::reload))
            .child(
                CommandNode.literal("give")
                    .usage(ArtMessages.USAGE_GIVE)
                    .permission(ArtPermissions.GIVE)
                    .arg(PLAYER, ArgumentTypes.playerRef())
                    .arg(NAME, artName())
                    .optionalArg(COUNT, ArgumentTypes.integer(1, MAX_COUNT))
                    .executes(this::give));
    }

    /**
     * Имя арта с подсказкой зарегистрированных: команда без подсказки заставляла бы помнить имена
     * наизусть, а они и так есть в реестре.
     */
    private ArgumentType<String> artName() {
        return new ArgumentType<String>() {

            @Override
            public String parse(String raw) {
                return raw;
            }

            @Override
            public List<String> suggestions(CommandSender sender, String partial) {
                ArtStore active = store.get();
                if (active == null) {
                    return Collections.emptyList();
                }
                String prefix = partial.toLowerCase(Locale.ROOT);
                List<String> found = new ArrayList<>();
                for (ArtEntry entry : active.entries()) {
                    if (entry.isReady() && entry.name()
                        .startsWith(prefix)) {
                        found.add(entry.name());
                    }
                }
                return found;
            }
        };
    }

    private void list(CommandContext context) {
        ArtStore active = store.get();
        if (active == null) {
            context.replyError(ArtMessages.OFF);
            return;
        }
        List<ArtEntry> entries = active.entries();
        if (entries.isEmpty()) {
            context.reply(ArtMessages.EMPTY);
            return;
        }
        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = Math.min(pages, context.getOrDefault(PAGE, 1));
        context.reply(ArtMessages.LIST_HEADER, page, pages, entries.size());
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        for (int i = from; i < to; i++) {
            ArtEntry entry = entries.get(i);
            if (entry.isReady()) {
                ArtDefinition definition = entry.definition();
                context.reply(
                    ArtMessages.LIST_ROW,
                    entry.name(),
                    definition.widthBlocks(),
                    definition.heightBlocks(),
                    definition.pixelWidth(),
                    definition.pixelHeight(),
                    definition.frameCount());
            } else {
                context.reply(ArtMessages.LIST_ROW_FAILED, entry.name(), entry.error());
            }
        }
    }

    private void info(CommandContext context) {
        ArtStore active = store.get();
        if (active == null) {
            context.replyError(ArtMessages.OFF);
            return;
        }
        String name = context.get(NAME);
        ArtEntry entry = active.entry(name);
        if (entry == null) {
            context.replyError(ArtMessages.UNKNOWN, name);
            return;
        }
        context.reply(ArtMessages.INFO_HEADER, entry.name());
        context.reply(
            ArtMessages.INFO_SOURCE,
            entry.record()
                .source());
        context.reply(
            ArtMessages.INFO_CANVAS,
            entry.record()
                .widthBlocks(),
            entry.record()
                .heightBlocks());
        if (!entry.isReady()) {
            context.replyError(ArtMessages.INFO_FAILED, entry.error());
            return;
        }
        ArtDefinition definition = entry.definition();
        context.reply(
            ArtMessages.INFO_IMAGE,
            definition.pixelWidth(),
            definition.pixelHeight(),
            definition.format()
                .name(),
            definition.byteSize(),
            definition.frameCount());
        context.reply(ArtMessages.INFO_HASH, definition.sha256());
        context.reply(definition.directUrl() == null ? ArtMessages.INFO_DIRECT_OFF : ArtMessages.INFO_DIRECT_ON);
    }

    private void add(CommandContext context) {
        ArtStore active = store.get();
        if (active == null) {
            context.replyError(ArtMessages.OFF);
            return;
        }
        String name = context.get(NAME);
        name = name.toLowerCase(Locale.ROOT);
        if (!ArtDefinition.isValidName(name)) {
            context.replyError(ArtMessages.INVALID_NAME, name);
            return;
        }
        int width = context.get(WIDTH);
        int height = context.get(HEIGHT);
        ArtSettings.Limits limits = settings.get().limits;
        if (width > limits.canvasWidth() || height > limits.canvasHeight()) {
            context.replyError(ArtMessages.CANVAS_TOO_BIG, width, height, limits.canvasWidth(), limits.canvasHeight());
            return;
        }
        String source = context.get(SOURCE);
        context.reply(ArtMessages.RESOLVING, name);
        active.add(ArtRecord.of(name, width, height, source), feedback(context));
    }

    private void remove(CommandContext context) {
        ArtStore active = store.get();
        if (active == null) {
            context.replyError(ArtMessages.OFF);
            return;
        }
        active.remove(context.get(NAME), feedback(context));
    }

    private void reload(CommandContext context) {
        ArtStore active = store.get();
        if (active == null) {
            context.replyError(ArtMessages.OFF);
            return;
        }
        active.reload(feedback(context));
    }

    /**
     * Выдача полотен предметом: игра складывает в инвентарь сколько влезет, остаток остаётся в переданном
     * стеке, и молча выбросить его нельзя. Невлезшее роняется игроку под ноги, а ответ несёт число
     * фактически выданных, а не запрошенных.
     */
    private void give(CommandContext context) {
        ArtStore active = store.get();
        if (active == null) {
            context.replyError(ArtMessages.OFF);
            return;
        }
        String name = context.get(NAME);
        ArtDefinition definition = active.definition(name);
        if (definition == null) {
            context.replyError(ArtMessages.UNKNOWN, name);
            return;
        }
        int count = context.getOrDefault(COUNT, 1);
        PlayerRef requested = context.get(PLAYER);
        EntityPlayerMP player = PlayerRefs.online(requested);
        if (player == null) {
            context.replyError(ArtMessages.PLAYER_OFFLINE, requested.name());
            return;
        }
        ItemStack stack = ArtItems.createStack(definition, count);
        if (stack == null || !player.inventory.addItemStackToInventory(stack)) {
            context.replyError(ArtMessages.GIVE_FAILED);
            return;
        }
        int given = count - stack.stackSize;
        if (stack.stackSize > 0) {
            player.dropPlayerItemWithRandomChoice(stack, false);
        }
        player.inventoryContainer.detectAndSendChanges();
        context.reply(ArtMessages.GIVE_DONE, given, definition.name(), player.getCommandSenderName());
    }

    private static ArtStore.Callback feedback(final CommandContext context) {
        return (success, messageKey, arguments) -> {
            if (success) {
                context.reply(messageKey, arguments);
            } else {
                context.replyError(messageKey, arguments);
            }
        };
    }
}
