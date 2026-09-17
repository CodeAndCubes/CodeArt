package com.mrleonardos.codeart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeart.internal.ArtMessages;

class ArtLangTest {

    @Test
    void bothLanguagesCarryTheSameKeys() throws IOException {
        assertEquals(keys(russian()).keySet(), keys(english()).keySet(), "наборы ключей ru_RU и en_US расходятся");
    }

    @Test
    void everyDeclaredKeyIsPresentInBothLanguages() throws IOException {
        Set<String> required = declared();
        for (Path file : Arrays.asList(russian(), english())) {
            Set<String> missing = new TreeSet<>(required);
            missing.removeAll(keys(file).keySet());
            assertTrue(
                missing.isEmpty(),
                () -> file.getFileName() + " не хватает ключей: " + String.join(", ", missing));
        }
    }

    @Test
    void keysStayInsideTheModNamespace() throws IOException {
        for (Path file : Arrays.asList(russian(), english())) {
            for (String key : keys(file).keySet()) {
                assertTrue(
                    key.startsWith("codeart.") || key.equals("itemGroup.codeart")
                        || key.startsWith("item.codeart.")
                        || key.startsWith("tile.codeart."),
                    () -> file.getFileName() + " держит чужой ключ " + key);
            }
        }
    }

    private static Set<String> declared() {
        Set<String> declared = new TreeSet<>();
        for (java.lang.reflect.Field field : ArtMessages.class.getFields()) {
            if (field.getType() == String.class) {
                try {
                    declared.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("не удалось прочитать " + field.getName(), e);
                }
            }
        }
        assertTrue(declared.size() > 30, "ключей перевода неожиданно мало: " + declared.size());
        return declared;
    }

    private static Path russian() {
        return Paths.get("src", "main", "resources", "assets", "codeart", "lang", "ru_RU.lang");
    }

    private static Path english() {
        return Paths.get("src", "main", "resources", "assets", "codeart", "lang", "en_US.lang");
    }

    private static Map<String, String> keys(Path file) throws IOException {
        Map<String, String> keys = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            assertTrue(separator > 0, () -> file.getFileName() + " держит строку без ключа: " + line);
            keys.put(trimmed.substring(0, separator), trimmed.substring(separator + 1));
        }
        return keys;
    }
}
