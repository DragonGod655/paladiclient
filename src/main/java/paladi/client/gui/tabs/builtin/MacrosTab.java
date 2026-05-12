/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.tabs.builtin;

import paladi.client.gui.GuiTheme;
import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.screens.EditSystemScreen;
import paladi.client.gui.tabs.Tab;
import paladi.client.gui.tabs.TabScreen;
import paladi.client.gui.tabs.WindowTabScreen;
import paladi.client.gui.widgets.containers.WTable;
import paladi.client.gui.widgets.pressable.WButton;
import paladi.client.gui.widgets.pressable.WConfirmedMinus;
import paladi.client.settings.Settings;
import paladi.client.systems.macros.Macro;
import paladi.client.systems.macros.Macros;
import paladi.client.utils.misc.NbtUtils;
import net.minecraft.client.gui.screen.Screen;

import static paladi.client.PaladiClient.mc;

public class MacrosTab extends Tab {
    public MacrosTab() {
        super("Macros");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new MacrosScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof MacrosScreen;
    }

    private static class MacrosScreen extends WindowTabScreen {
        public MacrosScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
        }

        @Override
        public void initWidgets() {
            WTable table = add(theme.table()).expandX().minWidth(400).widget();
            initTable(table);

            add(theme.horizontalSeparator()).expandX();

            WButton create = add(theme.button("Create")).expandX().widget();
            create.action = () -> mc.setScreen(new EditMacroScreen(theme, null, this::reload));
        }

        private void initTable(WTable table) {
            table.clear();
            if (Macros.get().isEmpty()) return;

            for (Macro macro : Macros.get()) {
                table.add(theme.label(macro.name.get() + " (" + macro.keybind.get() + ")"));

                WButton edit = table.add(theme.button(GuiRenderer.EDIT)).expandCellX().right().widget();
                edit.action = () -> mc.setScreen(new EditMacroScreen(theme, macro, this::reload));

                WConfirmedMinus remove = table.add(theme.confirmedMinus()).widget();
                remove.action = () -> {
                    Macros.get().remove(macro);
                    reload();
                };

                table.row();
            }
        }

        @Override
        public boolean toClipboard() {
            return NbtUtils.toClipboard(Macros.get());
        }

        @Override
        public boolean fromClipboard() {
            return NbtUtils.fromClipboard(Macros.get());
        }
    }

    private static class EditMacroScreen extends EditSystemScreen<Macro> {
        public EditMacroScreen(GuiTheme theme, Macro value, Runnable reload) {
            super(theme, value, reload);
        }

        @Override
        public Macro create() {
            return new Macro();
        }

        @Override
        public boolean save() {
            if (value.name.get().isBlank()
                || value.messages.get().isEmpty()
            ) return false;

            if (isNew) {
                for (Macro m : Macros.get()) {
                    if (value.equals(m)) return false;
                }
            }

            if (isNew) Macros.get().add(value);
            else Macros.get().save();

            return true;
        }

        @Override
        public Settings getSettings() {
            return value.settings;
        }
    }
}
