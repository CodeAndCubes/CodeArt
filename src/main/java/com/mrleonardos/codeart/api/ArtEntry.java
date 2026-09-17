package com.mrleonardos.codeart.api;

import java.nio.file.Path;

/**
 * Запись реестра после разрешения источника: либо готовый арт, либо причина, почему он не собрался.
 *
 * <p>
 * Неготовая запись не выпадает из реестра: администратор видит её в списке с причиной и правит источник,
 * не вспоминая, что там было. Файл с байтами есть только у готового арта.
 */
public final class ArtEntry {

    private final ArtRecord record;
    private final ArtDefinition definition;
    private final Path contentFile;
    private final String error;

    private ArtEntry(ArtRecord record, ArtDefinition definition, Path contentFile, String error) {
        this.record = record;
        this.definition = definition;
        this.contentFile = contentFile;
        this.error = error;
    }

    public static ArtEntry ready(ArtRecord record, ArtDefinition definition, Path contentFile) {
        return new ArtEntry(record, definition, contentFile, null);
    }

    public static ArtEntry failed(ArtRecord record, String error) {
        return new ArtEntry(record, null, null, error);
    }

    /** Запись реестра как она записана в манифесте. */
    public ArtRecord record() {
        return record;
    }

    /** Определение арта или {@code null} у неготовой записи. */
    public ArtDefinition definition() {
        return definition;
    }

    /** Файл с байтами картинки или {@code null} у неготовой записи и арта без файла. */
    public Path contentFile() {
        return contentFile;
    }

    /** Техническая причина, по которой арт не собрался. Есть только у неготовой записи. */
    public String error() {
        return error;
    }

    /** Разрешился ли источник и собралось ли определение. */
    public boolean isReady() {
        return definition != null;
    }

    public String name() {
        return record.name();
    }
}
