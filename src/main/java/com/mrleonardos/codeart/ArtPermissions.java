package com.mrleonardos.codeart;

/**
 * Ноды прав мода.
 *
 * <p>
 * Управление реестром артов и выдача полотен разделены: администратор наполняет сервер картинками, а
 * раздавать предметы игрокам может отдельный модератор. Смотреть реестр можно без права что-либо менять.
 *
 * <p>
 * Нет сервиса прав, значит ответ «нет». Открытый отказ раздал бы то, что админ закрыл нодой.
 */
public final class ArtPermissions {

    /** {@code /art list} и {@code /art info}: посмотреть реестр. */
    public static final String VIEW = "codeart.view";

    /** {@code /art add}, {@code /art remove}, {@code /art reload}: управление реестром артов. */
    public static final String ADMIN = "codeart.admin";

    /** {@code /art give}: выдать игроку полотно предметом. */
    public static final String GIVE = "codeart.give";

    private ArtPermissions() {}
}
