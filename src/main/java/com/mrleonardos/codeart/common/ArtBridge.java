package com.mrleonardos.codeart.common;

/**
 * Связывает пакеты со стороной, которая умеет их обработать.
 *
 * <p>
 * Каждая сторона подставляет свою реализацию при подъёме. Пакеты общие для обеих сторон, а реестр клиента
 * и перекачка байтов живут в разных половинах: обработчик пакета обращается сюда, а не к ним напрямую,
 * потому что общий код не должен ссылаться на вырезаемые классы.
 */
public final class ArtBridge {

    private static ClientArtSink client;

    private static ServerArtSink server;

    private ArtBridge() {}

    public static void client(ClientArtSink sink) {
        client = sink;
    }

    public static void server(ServerArtSink sink) {
        server = sink;
    }

    /** Клиентская половина или {@code null}, если её в этой сборке нет. */
    public static ClientArtSink client() {
        return client;
    }

    /** Серверная половина или {@code null}, если она ещё не поднята. */
    public static ServerArtSink server() {
        return server;
    }
}
