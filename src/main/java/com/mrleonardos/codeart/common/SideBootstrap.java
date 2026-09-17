package com.mrleonardos.codeart.common;

import java.io.File;

/**
 * То, что клиентская сторона делает при запуске.
 *
 * <p>
 * Класс подставляется по имени через {@code @SidedProxy}, поэтому общий код на него не ссылается, и
 * клиентская половина спокойно вырезается из серверной сборки.
 */
public interface SideBootstrap {

    /** Поднять свою половину. */
    void install(File gameDirectory);
}
