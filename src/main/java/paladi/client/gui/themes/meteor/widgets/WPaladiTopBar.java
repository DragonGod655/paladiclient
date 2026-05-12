/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets;

import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.WTopBar;
import paladi.client.utils.render.color.Color;

public class WPaladiTopBar extends WTopBar implements PaladiWidget {
    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return theme().backgroundColor.get(pressed, hovered);
    }

    @Override
    protected Color getNameColor() {
        return theme().textColor.get();
    }
}
