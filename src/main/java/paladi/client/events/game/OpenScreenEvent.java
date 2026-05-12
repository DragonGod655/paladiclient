/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.events.game;

import paladi.client.events.Cancellable;
import net.minecraft.client.gui.screen.Screen;

public class OpenScreenEvent extends Cancellable {
    private static final OpenScreenEvent INSTANCE = new OpenScreenEvent();

    public Screen screen;

    public static OpenScreenEvent get(Screen screen) {
        INSTANCE.setCancelled(false);
        INSTANCE.screen = screen;
        return INSTANCE;
    }
}
