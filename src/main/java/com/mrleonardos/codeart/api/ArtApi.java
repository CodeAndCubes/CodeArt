package com.mrleonardos.codeart.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Точка входа для чужих модов.
 *
 * <pre>
 *
 * ArtApi.registerStore(new SqlArtStore());
 * ArtDefinition poster = ArtApi.active()
 *     .orElseThrow()
 *     .definition("poster");
 * </pre>
 *
 * <p>
 * Хранилище регистрируют на инициализации своего мода: реестр закрывается в постинициализации CodeArt, и
 * только после этого выбирается активный провайдер. Регистрация после заморозки отклоняется
 * {@code IllegalStateException}.
 *
 * <p>
 * {@link #active()} ведёт к хранилищу, которое держит сервер, каким бы модулем оно ни было принесено.
 * Обращение до подъёма CodeArt даёт пустую ссылку, а не падение.
 */
public final class ArtApi {

    private static final Map<String, ArtStore> STORES = new LinkedHashMap<>();

    private static volatile boolean frozen;

    private static volatile Supplier<ArtStore> installed;

    private ArtApi() {}

    /**
     * Зарегистрировать хранилище. Провайдер с уже занятым именем заменяет прежнего, активным становится
     * тот, чьё имя стоит в {@code [storage] provider} главного файла линейки.
     *
     * @throws IllegalStateException если реестр уже заморожен
     */
    public static synchronized void registerStore(ArtStore store) {
        Objects.requireNonNull(store, "store");
        ensureOpen();
        STORES.put(store.id(), store);
    }

    /** Хранилище по имени из {@code [storage] provider} главного файла. */
    public static synchronized Optional<ArtStore> store(String id) {
        return Optional.ofNullable(STORES.get(Objects.requireNonNull(id, "id")));
    }

    /** Все зарегистрированные хранилища в порядке регистрации. */
    public static synchronized List<ArtStore> stores() {
        return new ArrayList<>(STORES.values());
    }

    /** Закрыть реестр для новых хранилищ. Вызывается самим CodeArt. */
    public static synchronized void freeze() {
        frozen = true;
    }

    /**
     * Подключает действующее хранилище. Вызывается самим CodeArt: чужим модам метод не нужен.
     *
     * @param store откуда брать хранилище; это поставщик, а не готовая ссылка, чтобы хранилище,
     *              пересобранное перезапуском сервера, было видно и через эту дверь
     */
    public static void install(Supplier<ArtStore> store) {
        installed = Objects.requireNonNull(store, "store");
    }

    /** Действующее хранилище или пустая ссылка, пока сервер его не поднял. */
    public static Optional<ArtStore> active() {
        Supplier<ArtStore> holder = installed;
        return holder == null ? Optional.empty() : Optional.ofNullable(holder.get());
    }

    /**
     * Действующее хранилище.
     *
     * @throws IllegalStateException если CodeArt ещё не поднялся или его хранилище выключено
     */
    public static ArtStore service() {
        return active()
            .orElseThrow(() -> new IllegalStateException("No art store is running: CodeArt is off or not started yet"));
    }

    private static void ensureOpen() {
        if (frozen) {
            throw new IllegalStateException("ArtApi registry is frozen, register stores in init");
        }
    }
}
