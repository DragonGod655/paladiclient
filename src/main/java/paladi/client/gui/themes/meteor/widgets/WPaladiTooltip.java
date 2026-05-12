/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.WTooltip;

public class WPaladiTooltip extends WTooltip implements PaladiWidget {
    public WPaladiTooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.quad(this, theme().backgroundColor.get());
    }
}
