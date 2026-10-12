/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.widgets;

import paladi.client.PaladiClient;
import paladi.client.events.Paladi.KeyEvent;
import paladi.client.events.Paladi.MouseClickEvent;
import net.minecraft.client.gui.Click;
import paladi.client.gui.widgets.containers.WHorizontalList;
import paladi.client.gui.widgets.pressable.WButton;
import paladi.client.systems.modules.Modules;
import paladi.client.utils.misc.Keybind;
import paladi.client.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

public class WKeybind extends WHorizontalList {
    public Runnable action;
    public Runnable actionOnSet;

    private WButton button;

    private final Keybind keybind;
    private final Keybind defaultValue;
    private boolean listening;

    public WKeybind(Keybind keybind, Keybind defaultValue) {
        this.keybind = keybind;
        this.defaultValue = defaultValue;
        PaladiClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void init() {
        button = add(theme.button("")).widget();
        button.action = () -> {
            listening = true;
            button.set("...");

            if (actionOnSet != null) actionOnSet.run();
        };

        refreshLabel();
    }

    @Override
    public boolean onKeyPressed(KeyInput input) {
        if (listening) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_BACKSPACE || input.key() == GLFW.GLFW_KEY_DELETE) {
                return onClear();
            }
            return onAction(true, input.key(), input.modifiers());
        }
        return super.onKeyPressed(input);
    }

    @Override
    public boolean onMouseClicked(Click click, boolean doubled) {
        if (listening) {
            return onAction(false, click.button(), 0);
        }
        return super.onMouseClicked(click, doubled);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onKeyEvent(KeyEvent event) {
        if (!listening) return;
        if (event.action == KeyAction.Press) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE) {
                onClear();
                event.cancel();
            } else if (onAction(true, event.key(), event.modifiers())) {
                event.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMouseClickEvent(MouseClickEvent event) {
        if (!listening) return;
        if (event.action == KeyAction.Press) {
            if (onAction(false, event.button(), 0)) {
                event.cancel();
            }
        }
    }

    public boolean onClear() {
        if (listening) {
            keybind.reset();
            reset();

            return true;
        }

        return false;
    }

    public boolean onAction(boolean isKey, int value, int modifiers) {
        if (listening && keybind.canBindTo(isKey, value, modifiers)) {
            keybind.set(isKey, value, modifiers);
            reset();

            return true;
        }

        return false;
    }

    public void resetBind() {
        keybind.set(defaultValue);
        reset();
    }

    public void reset() {
        listening = false;
        refreshLabel();
        if (Modules.get().isBinding()) {
            Modules.get().setModuleToBind(null);
        }
        if (action != null) action.run();

        // Auto save immediately upon changing or resetting keybind
        Modules.get().save();
    }

    private void refreshLabel() {
        button.set(keybind.toString());
    }
}
