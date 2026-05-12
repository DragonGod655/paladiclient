/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets.pressable;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.pressable.WMinus;

public class WPaladiMinus extends WMinus implements PaladiWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double pad = pad();
        double s = theme.scale(3);

        renderBackground(renderer, this, pressed, mouseOver);
        renderer.quad(x + pad, y + height / 2 - s / 2, width - pad * 2, s, theme().minusColor.get());
    }
}
