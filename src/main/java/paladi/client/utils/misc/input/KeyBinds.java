/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.misc.input;

import paladi.client.PaladiClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(PaladiClient.identifier("paladi-client"));

    public static KeyBinding OPEN_GUI = new KeyBinding("key.paladi-client.open-gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY);
    public static KeyBinding OPEN_COMMANDS = new KeyBinding("key.paladi-client.open-commands", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, CATEGORY);

    private KeyBinds() {
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
