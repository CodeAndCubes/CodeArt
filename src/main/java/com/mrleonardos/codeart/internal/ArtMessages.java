package com.mrleonardos.codeart.internal;

/**
 * Ключи перевода мода.
 *
 * <p>
 * Тексты живут отдельной веткой имён от нод прав: подсказки под {@code codeart.command.*}, сообщения под
 * {@code codeart.message.*}, а сами ноды это {@code codeart.view}, {@code codeart.admin} и
 * {@code codeart.give} из {@code ArtPermissions}. Иначе выдача по маске {@code codeart.*} попадала бы
 * разом на право и на строку перевода.
 */
public final class ArtMessages {

    public static final String USAGE_ROOT = "codeart.command.usage";
    public static final String USAGE_LIST = "codeart.command.usage.list";
    public static final String USAGE_INFO = "codeart.command.usage.info";
    public static final String USAGE_ADD = "codeart.command.usage.add";
    public static final String USAGE_REMOVE = "codeart.command.usage.remove";
    public static final String USAGE_GIVE = "codeart.command.usage.give";

    public static final String OFF = "codeart.message.off";
    public static final String EMPTY = "codeart.message.empty";
    public static final String LIST_HEADER = "codeart.message.list.header";
    public static final String LIST_ROW = "codeart.message.list.row";
    public static final String LIST_ROW_FAILED = "codeart.message.list.row_failed";
    public static final String INFO_HEADER = "codeart.message.info.header";
    public static final String INFO_SOURCE = "codeart.message.info.source";
    public static final String INFO_CANVAS = "codeart.message.info.canvas";
    public static final String INFO_FAILED = "codeart.message.info.failed";
    public static final String INFO_IMAGE = "codeart.message.info.image";
    public static final String INFO_HASH = "codeart.message.info.hash";
    public static final String INFO_DIRECT_ON = "codeart.message.info.direct_on";
    public static final String INFO_DIRECT_OFF = "codeart.message.info.direct_off";
    public static final String RESOLVING = "codeart.message.resolving";
    public static final String TOOLTIP_SIZE = "codeart.tooltip.size";
    public static final String PLACE_DENIED = "codeart.message.place_denied";
    public static final String NO_SPACE = "codeart.message.no_space";

    public static final String RELOAD_DONE = "codeart.message.reload.done";
    public static final String RELOAD_FAILED = "codeart.message.reload.failed";
    public static final String ADD_DONE = "codeart.message.add.done";
    public static final String ADD_FAILED = "codeart.message.add.failed";
    public static final String EXISTS = "codeart.message.exists";
    public static final String REMOVE_DONE = "codeart.message.remove.done";
    public static final String UNKNOWN = "codeart.message.unknown";
    public static final String INVALID_NAME = "codeart.message.invalid_name";
    public static final String CANVAS_TOO_BIG = "codeart.message.canvas_too_big";

    public static final String GIVE_DONE = "codeart.message.give.done";
    public static final String GIVE_FAILED = "codeart.message.give.failed";
    public static final String PLAYER_OFFLINE = "codeart.message.player_offline";

    private ArtMessages() {}
}
