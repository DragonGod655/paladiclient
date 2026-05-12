/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.WMultiLabel;
import paladi.client.utils.render.color.Color;

public class WPaladiMultiLabel extends WMultiLabel implements PaladiWidget {
    public WPaladiMultiLabel(String text, boolean title, double maxWidth) {
        super(text, title, maxWidth);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double h = theme.textHeight(title);
        Color defaultColor = theme().textColor.get();

        for (int i = 0; i < lines.size(); i++) {
            renderer.text(lines.get(i), x, y + h * i, color != null ? color : defaultColor, false);
        }
    }
}
