package com.mrleonardos.codeart.internal.store;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.api.ArtFormat;
import com.mrleonardos.codeart.api.ArtRecord;

public final class ArtManifestFile {

    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private ArtManifestFile() {}

    public static List<ArtRecord> read(Path file, Logger log) throws IOException {
        List<ArtRecord> records = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return records;
        }
        JsonElement root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(reader);
        } catch (RuntimeException e) {
            throw new IOException("Cannot parse " + file.getFileName(), e);
        }
        if (root == null || !root.isJsonObject()) {
            throw new IOException(file.getFileName() + " must contain a json object");
        }
        JsonElement arts = root.getAsJsonObject()
            .get("arts");
        if (arts == null || !arts.isJsonArray()) {
            return records;
        }
        JsonArray array = arts.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                log.warn("Skipping art entry #{}: not a json object", i);
                continue;
            }
            ArtRecord record = readRecord(element.getAsJsonObject(), i, log);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    public static void write(Path file, List<ArtRecord> records, Logger log) throws IOException {
        JsonArray array = new JsonArray();
        for (ArtRecord record : records) {
            JsonObject object = new JsonObject();
            object.addProperty("name", record.name());
            object.addProperty("width", record.widthBlocks());
            object.addProperty("height", record.heightBlocks());
            object.addProperty("source", record.source());
            if (record.hasMetadata()) {
                object.addProperty("sha256", record.sha256());
                object.addProperty(
                    "format",
                    record.format()
                        .name()
                        .toLowerCase(Locale.ROOT));
                object.addProperty("bytes", record.byteSize());
                object.addProperty("pixelWidth", record.pixelWidth());
                object.addProperty("pixelHeight", record.pixelHeight());
                object.addProperty("frames", record.frameCount());
            }
            array.add(object);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("arts", array);

        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static ArtRecord readRecord(JsonObject object, int index, Logger log) {
        String name = optionalString(object, "name");
        if (!ArtDefinition.isValidName(name)) {
            log.warn("Skipping art entry #{}: invalid name '{}'", index, name);
            return null;
        }
        int width = optionalInt(object, "width", 0);
        int height = optionalInt(object, "height", 0);
        if (width < 1 || height < 1) {
            log.warn("Skipping art '{}': invalid size {}x{}", name, width, height);
            return null;
        }
        String source = optionalString(object, "source");
        if (source == null || source.trim()
            .isEmpty()) {
            log.warn("Skipping art '{}': missing source", name);
            return null;
        }
        ArtRecord base = ArtRecord.of(name, width, height, source);
        String sha256 = optionalString(object, "sha256");
        ArtFormat format = parseFormat(optionalString(object, "format"));
        int bytes = optionalInt(object, "bytes", 0);
        int pixelWidth = optionalInt(object, "pixelWidth", 0);
        int pixelHeight = optionalInt(object, "pixelHeight", 0);
        int frames = optionalInt(object, "frames", 0);
        if (sha256 == null || format == null) {
            return base;
        }
        ArtRecord withMetadata = new ArtRecord(
            name,
            width,
            height,
            source,
            sha256,
            format,
            bytes,
            pixelWidth,
            pixelHeight,
            frames);
        return withMetadata.hasMetadata() ? withMetadata : base;
    }

    private static ArtFormat parseFormat(String value) {
        if (value == null) {
            return null;
        }
        for (ArtFormat format : ArtFormat.values()) {
            if (format.name()
                .equalsIgnoreCase(value)
                || format.extension()
                    .equalsIgnoreCase(value)) {
                return format;
            }
        }
        return null;
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static int optionalInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
