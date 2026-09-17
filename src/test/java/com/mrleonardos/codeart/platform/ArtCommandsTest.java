package com.mrleonardos.codeart.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeart.ArtPermissions;
import com.mrleonardos.codeart.internal.ArtMessages;
import com.mrleonardos.codeart.internal.ArtSettings;
import com.mrleonardos.codecore.api.command.CommandNode;

/**
 * Дерево {@code /art}: каждая ветка несёт право и подсказку. Ветка без права видна всем, а подсказка
 * без ключа перевода доехала бы до игрока сырой строкой.
 */
class ArtCommandsTest {

    @Test
    void everyBranchCarriesAPermissionAndAKnownUsageKey() {
        CommandNode root = new ArtCommands(() -> null, ArtSettings::defaults).root();

        assertEquals("art", root.name());
        assertEquals(ArtMessages.USAGE_ROOT, root.usageKey());
        assertEquals(
            6,
            root.children()
                .size());
        for (CommandNode child : root.children()) {
            assertNotNull(child.permissionNode(), "ветка /art " + child.name() + " без права видна всем");
            assertTrue(
                child.permissionNode()
                    .equals(ArtPermissions.VIEW)
                    || child.permissionNode()
                        .equals(ArtPermissions.ADMIN)
                    || child.permissionNode()
                        .equals(ArtPermissions.GIVE),
                "ветка /art " + child.name() + " несёт незнакомое право " + child.permissionNode());
            if (child.usageKey() != null) {
                assertTrue(
                    knownKeys().contains(child.usageKey()),
                    "ветка /art " + child.name() + " ссылается на ключ перевода мимо ArtMessages");
            }
        }
    }

    @Test
    void managementBranchesAreAdminOnes() {
        CommandNode root = new ArtCommands(() -> null, ArtSettings::defaults).root();

        assertEquals(ArtPermissions.ADMIN, permissionOf(root, "add"));
        assertEquals(ArtPermissions.ADMIN, permissionOf(root, "remove"));
        assertEquals(ArtPermissions.ADMIN, permissionOf(root, "reload"));
        assertEquals(ArtPermissions.VIEW, permissionOf(root, "list"));
        assertEquals(ArtPermissions.VIEW, permissionOf(root, "info"));
        assertEquals(ArtPermissions.GIVE, permissionOf(root, "give"));
    }

    private static String permissionOf(CommandNode root, String name) {
        for (CommandNode child : root.children()) {
            if (child.name()
                .equals(name)) {
                return child.permissionNode();
            }
        }
        throw new AssertionError("ветки /art " + name + " нет в дереве");
    }

    private static List<String> knownKeys() {
        List<String> keys = new ArrayList<>();
        for (java.lang.reflect.Field field : ArtMessages.class.getFields()) {
            if (field.getType() == String.class) {
                try {
                    keys.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("не удалось прочитать " + field.getName(), e);
                }
            }
        }
        return keys;
    }
}
