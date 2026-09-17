package com.mrleonardos.codeart;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeart.api.ArtApi;
import com.mrleonardos.codesides.gate.PackageGate;

/**
 * Границы, которые держат мод собираемым в два jar.
 *
 * <p>
 * Вырезается из серверного сборки только клиентская половина: мод двусторонний с обязательным клиентом,
 * и серверная логика нужна клиентскому jar целиком, иначе одиночная игра осталась бы без артов. Поэтому
 * проверка одна, но жёсткая: ни один пакет вне {@code client} не ссылается на клиентский, и ни один
 * пакет вне {@code platform} не знает типов игры.
 */
class ImportGateTest {

    private static final String API = "com/mrleonardos/codeart/api";
    private static final String INTERNAL = "com/mrleonardos/codeart/internal";
    private static final String PLATFORM = "com/mrleonardos/codeart/platform";
    private static final String CLIENT = "com/mrleonardos/codeart/client";
    private static final String COMMON = "com/mrleonardos/codeart/common";
    private static final String NETWORK = "com/mrleonardos/codeart/network";

    /**
     * Шире заводского списка гейта: тот знает только про игру, а из мода торчала ещё netty вместе с
     * пакетами и слой платформы ядра, которого в api-джаре ядра тоже нет.
     */
    private static final String[] FOREIGN = { "net/minecraft", "net/minecraftforge", "cpw/mods", "io/netty",
        "org/lwjgl", "com/mojang", "com/mrleonardos/codecore/platform", "com/mrleonardos/codecore/internal" };

    @Test
    void apiAndInternalHoldNoPlatformTypes() throws IOException {
        List<String> violations = gate(FOREIGN).violations(API, INTERNAL);

        assertTrue(
            violations.isEmpty(),
            () -> "типы игры, netty и слои платформы ядра живут только в platform и client, чужие ссылки:\n"
                + String.join("\n", violations));
    }

    @Test
    void theWireHoldsNoPlatformTypesEither() throws IOException {
        List<String> violations = gate(FOREIGN).violations(COMMON, NETWORK);

        assertTrue(
            violations.isEmpty(),
            () -> "пакеты и мост описывают провод, а не игру: игрок едет ссылкой PlayerRef, байты буфером ядра. "
                + "Чужие ссылки:\n"
                + String.join("\n", violations));
    }

    @Test
    void nothingOutsideTheClientHalfReachesIntoIt() throws IOException {
        List<String> violations = gate(CLIENT).violations(API, INTERNAL, COMMON, NETWORK, PLATFORM);

        assertTrue(
            violations.isEmpty(),
            () -> "клиентский пакет вырезается из серверного jar, ссылки на него ведут в пустоту:\n"
                + String.join("\n", violations));
    }

    @Test
    void theClientHalfKnowsNoMinecraftTypesOfItsOwn() throws IOException {
        List<String> violations = gate("io/netty", "com/mojang").violations(CLIENT);

        assertTrue(
            violations.isEmpty(),
            () -> "клиентскому пакету netty не нужна, а mojang это чужое имя:\n" + String.join("\n", violations));
    }

    @Test
    void theEntryPointStaysSideFree() throws IOException {
        PackageGate gate = gate(CLIENT);

        for (Class<?> shared : new Class<?>[] { CodeArtMod.class, ArtConstants.class, ArtPermissions.class }) {
            List<String> violations = gate.scan(bytesOf(shared));
            assertTrue(
                violations.isEmpty(),
                () -> shared.getSimpleName() + " лежит в обоих jar и не должен знать клиентской половины:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void eventListenersArePublic() throws IOException {
        List<String> hidden = gate().hiddenListeners();

        assertTrue(hidden.isEmpty(), () -> "классы с @SubscribeEvent обязаны быть public: " + hidden);
    }

    @Test
    void gateNoticesAForbiddenReference() throws IOException {
        List<String> found = gate().scan(PackageGate.foreignSample());

        assertFalse(found.isEmpty(), "гейт обязан ловить ссылку на тип Minecraft");
    }

    private static PackageGate gate(String... forbidden) throws IOException {
        return PackageGate.of(ArtApi.class, forbidden);
    }

    private static byte[] bytesOf(Class<?> type) throws IOException {
        String resource = "/" + type.getName()
            .replace('.', '/') + ".class";
        try (InputStream classFile = type.getResourceAsStream(resource)) {
            if (classFile == null) {
                throw new IOException("класс " + type.getName() + " не найден среди скомпилированных");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int read = classFile.read(chunk); read > 0; read = classFile.read(chunk)) {
                bytes.write(chunk, 0, read);
            }
            return bytes.toByteArray();
        }
    }
}
