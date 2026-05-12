/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.macros;

import paladi.client.PaladiClient;
import paladi.client.events.Paladi.KeyEvent;
import paladi.client.events.Paladi.MouseClickEvent;
import paladi.client.systems.System;
import paladi.client.systems.Systems;
import paladi.client.utils.misc.NbtUtils;
import paladi.client.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Macros extends System<Macros> implements Iterable<Macro> {
    private List<Macro> macros = new ArrayList<>();

    public Macros() {
        super("macros");
    }

    public static Macros get() {
        return Systems.get(Macros.class);
    }

    public void add(Macro macro) {
        macros.add(macro);
        PaladiClient.EVENT_BUS.subscribe(macro);
        save();
    }

    public Macro get(String name) {
        for (Macro macro : macros) {
            if (macro.name.get().equalsIgnoreCase(name)) return macro;
        }

        return null;
    }

    public List<Macro> getAll() {
        return macros;
    }

    public void remove(Macro macro) {
        if (macros.remove(macro)) {
            PaladiClient.EVENT_BUS.unsubscribe(macro);
            save();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onKey(KeyEvent event) {
        if (event.action == KeyAction.Release) return;

        for (Macro macro : macros) {
            if (macro.onAction(true, event.key(), event.modifiers())) return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouse(MouseClickEvent event) {
        if (event.action == KeyAction.Release) return;

        for (Macro macro : macros) {
            if (macro.onAction(false, event.button(), 0)) return;
        }
    }

    public boolean isEmpty() {
        return macros.isEmpty();
    }

    @Override
    public @NotNull Iterator<Macro> iterator() {
        return macros.iterator();
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.put("macros", NbtUtils.listToTag(macros));
        return tag;
    }

    @Override
    public Macros fromTag(NbtCompound tag) {
        for (Macro macro : macros) PaladiClient.EVENT_BUS.unsubscribe(macro);

        macros = NbtUtils.listFromTag(tag.getListOrEmpty("macros"), Macro::new);

        for (Macro macro : macros) PaladiClient.EVENT_BUS.subscribe(macro);
        return this;
    }
}
