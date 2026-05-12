/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets.pressable;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiGuiTheme;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.pressable.WConfirmedMinus;
import paladi.client.utils.render.color.Color;

public class WPaladiConfirmedMinus extends WConfirmedMinus implements PaladiWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        PaladiGuiTheme theme = theme();
        double pad = pad();
        double s = theme.scale(3);

        Color outline = theme.outlineColor.get(pressed, mouseOver);
        Color fg = pressedOnce ? theme.backgroundColor.get(pressed, mouseOver) : theme().minusColor.get();
        Color bg = pressedOnce ? theme().minusColor.get() : theme.backgroundColor.get(pressed, mouseOver);

        renderBackground(renderer, this, outline, bg);
        renderer.quad(x + pad, y + height / 2 - s / 2, width - pad * 2, s, fg);
    }
}
