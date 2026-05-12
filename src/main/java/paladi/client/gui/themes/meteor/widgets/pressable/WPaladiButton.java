/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets.pressable;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.renderer.packer.GuiTexture;
import paladi.client.gui.themes.Paladi.PaladiGuiTheme;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.pressable.WButton;

public class WPaladiButton extends WButton implements PaladiWidget {
    public WPaladiButton(String text, GuiTexture texture) {
        super(text, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        PaladiGuiTheme theme = theme();
        double pad = pad();

        renderBackground(renderer, this, pressed, mouseOver);

        if (text != null) {
            renderer.text(text, x + width / 2 - textWidth / 2, y + pad, theme.textColor.get(), false);
        }
        else {
            double ts = theme.textHeight();
            renderer.quad(x + width / 2 - ts / 2, y + pad, ts, ts, texture, theme.textColor.get());
        }
    }
}
