/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.tabs.builtin;

import paladi.client.gui.GuiTheme;
import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.tabs.Tab;
import paladi.client.gui.tabs.TabScreen;
import paladi.client.gui.tabs.WindowTabScreen;
import paladi.client.gui.widgets.containers.WContainer;
import paladi.client.gui.widgets.containers.WHorizontalList;
import paladi.client.gui.widgets.pressable.WButton;
import paladi.client.gui.widgets.pressable.WCheckbox;
import paladi.client.systems.hud.Hud;
import paladi.client.systems.hud.screens.HudEditorScreen;
import paladi.client.utils.misc.NbtUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import static paladi.client.PaladiClient.mc;

public class HudTab extends Tab {
    public HudTab() {
        super("HUD");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new HudScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof HudScreen;
    }

    public static class HudScreen extends WindowTabScreen {
        private WContainer settingsContainer;
        private final Hud hud;

        public HudScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);

            hud = Hud.get();
            hud.settings.onActivated();
        }

        @Override
        public void initWidgets() {
            settingsContainer = add(theme.verticalList()).expandX().widget();
            settingsContainer.add(theme.settings(hud.settings)).expandX().widget();

            add(theme.horizontalSeparator()).expandX();

            WButton openEditor = add(theme.button("Edit")).expandX().widget();
            openEditor.action = () -> mc.setScreen(new HudEditorScreen(theme));

            WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();
            buttons.add(theme.confirmedButton("Clear", "Confirm")).expandX().widget().action = hud::clear;
            buttons.add(theme.confirmedButton("Reset to default elements", "Confirm")).expandX().widget().action = hud::resetToDefaultElements;

            add(theme.horizontalSeparator()).expandX();

            WHorizontalList bottom = add(theme.horizontalList()).expandX().widget();

            bottom.add(theme.label("Active: "));
            WCheckbox active = bottom.add(theme.checkbox(hud.active)).expandCellX().widget();
            active.action = () -> hud.active = active.checked;

            WButton resetSettings = bottom.add(theme.button(GuiRenderer.RESET)).widget();
            resetSettings.action = hud.settings::reset;
            resetSettings.tooltip = "Reset";
        }

        @Override
        protected void onRenderBefore(DrawContext drawContext, float delta) {
            HudEditorScreen.renderElements(drawContext);
        }

        @Override
        public void tick() {
            super.tick();

            hud.settings.tick(settingsContainer, theme);
        }

        @Override
        public boolean toClipboard() {
            return NbtUtils.toClipboard(hud);
        }

        @Override
        public boolean fromClipboard() {
            return NbtUtils.fromClipboard(hud);
        }
    }
}
