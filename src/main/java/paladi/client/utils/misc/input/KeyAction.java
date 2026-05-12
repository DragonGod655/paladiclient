/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.misc.input;

import org.lwjgl.glfw.GLFW;

public enum KeyAction {
    Press,
    Repeat,
    Release;

    public static KeyAction get(int action) {
        return switch (action) {
            case GLFW.GLFW_PRESS -> Press;
            case GLFW.GLFW_RELEASE -> Release;
            default -> Repeat;
        };
    }
}
