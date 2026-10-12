/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.misc.input;

import net.fabricmc.loader.api.FabricLoader;

import paladi.client.PaladiClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(PaladiClient.identifier("paladi-client"));

    public static final boolean IS_DAWN = checkIsDawn();

    public static KeyBinding OPEN_GUI = new KeyBinding("key.paladi-client.open-gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY);
    public static KeyBinding OPEN_COMMANDS = new KeyBinding("key.paladi-client.open-commands", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, CATEGORY);

    private KeyBinds() {
    }

    public static boolean checkIsDawn() {
        try {
            // Check game directory (e.g. .dawn/profiles/...)
            String gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().toString().toLowerCase();
            if (gameDir.contains(".dawn") || gameDir.contains("dawn")) return true;

            // Check working directory
            String userDir = System.getProperty("user.dir", "").toLowerCase();
            if (userDir.contains(".dawn") || userDir.contains("dawn")) return true;

            // Check loaded mods
            for (var mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId().toLowerCase();
                String name = mod.getMetadata().getName().toLowerCase();
                if (id.contains("dawn") || name.contains("dawn")) return true;
            }

            // Check system properties
            for (String key : System.getProperties().stringPropertyNames()) {
                if (key.toLowerCase().contains("dawn")) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static int getDefaultGuiKey() {
        return GLFW.GLFW_KEY_RIGHT_CONTROL;
    }

    public static KeyBinding[] apply(KeyBinding[] binds) {
        // Add key binding
        KeyBinding[] newBinds = new KeyBinding[binds.length + 2];

        System.arraycopy(binds, 0, newBinds, 0, binds.length);
        newBinds[binds.length] = OPEN_GUI;
        newBinds[binds.length + 1] = OPEN_COMMANDS;

        return newBinds;
    }
}
